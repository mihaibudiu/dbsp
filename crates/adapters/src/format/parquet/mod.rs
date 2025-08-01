use std::io::Cursor;
use std::mem::take;
use std::{borrow::Cow, sync::Arc};

use actix_web::HttpRequest;
use anyhow::{bail, Result as AnyResult};
use arrow::datatypes::{
    DataType, Field as ArrowField, FieldRef, Fields, IntervalUnit as ArrowIntervalUnit, Schema,
    TimeUnit,
};
use bytes::Bytes;
use erased_serde::Serialize as ErasedSerialize;
use feldera_adapterlib::catalog::ArrowStream;
use feldera_types::config::ConnectorConfig;
use feldera_types::serde_with_context::serde_config::{
    BinaryFormat, DecimalFormat, UuidFormat, VariantFormat,
};
use feldera_types::serde_with_context::{DateFormat, SqlSerdeConfig, TimeFormat, TimestampFormat};
use parquet::arrow::arrow_reader::ParquetRecordBatchReader;
use parquet::arrow::ArrowWriter;
use parquet::file::properties::WriterProperties;
use serde::Deserialize;
use serde_arrow::schema::SerdeArrowSchema;
use serde_arrow::ArrayBuilder;
use serde_urlencoded::Deserializer as UrlDeserializer;
use serde_yaml::Value as YamlValue;

use crate::catalog::{CursorWithPolarity, SerBatchReader};
use crate::format::MAX_DUPLICATES;
use crate::{
    catalog::{InputCollectionHandle, RecordFormat},
    format::{Encoder, InputFormat, OutputFormat, ParseError, Parser},
    ControllerError, OutputConsumer, SerCursor,
};
use feldera_types::format::parquet::{ParquetEncoderConfig, ParquetParserConfig};
use feldera_types::program_schema::{ColumnType, Field, IntervalUnit, Relation, SqlType};

use super::{InputBuffer, Sponge};

#[cfg(test)]
pub mod test;

/// Default arrow serder config used to encode data in Parquet.
pub const fn default_arrow_serde_config() -> &'static SqlSerdeConfig {
    &SqlSerdeConfig {
        timestamp_format: TimestampFormat::MicrosSinceEpoch,
        time_format: TimeFormat::NanosSigned,
        date_format: DateFormat::String("%Y-%m-%d"),
        decimal_format: DecimalFormat::String,
        variant_format: VariantFormat::JsonString,
        binary_format: BinaryFormat::Array,
        uuid_format: UuidFormat::String,
    }
}

/// CSV format parser.
pub struct ParquetInputFormat;

impl InputFormat for ParquetInputFormat {
    fn name(&self) -> Cow<'static, str> {
        Cow::Borrowed("parquet")
    }

    /// Create a parser using configuration extracted from an HTTP request.
    // We could just rely on serde to deserialize the config from the
    // HTTP query, but a specialized method gives us more flexibility.
    fn config_from_http_request(
        &self,
        _endpoint_name: &str,
        _request: &HttpRequest,
    ) -> Result<Box<dyn ErasedSerialize>, ControllerError> {
        Ok(Box::new(ParquetParserConfig {}))
    }

    fn new_parser(
        &self,
        _endpoint_name: &str,
        input_stream: &InputCollectionHandle,
        _config: &YamlValue,
    ) -> Result<Box<dyn Parser>, ControllerError> {
        let input_stream = input_stream
            .handle
            .configure_arrow_deserializer(default_arrow_serde_config().clone())?;
        Ok(Box::new(ParquetParser::new(input_stream)) as Box<dyn Parser>)
    }
}

struct ParquetParser {
    /// Input handle to push parsed data to.
    input_stream: Box<dyn ArrowStream>,
    last_chunk_number: u64,
}

impl ParquetParser {
    fn new(input_stream: Box<dyn ArrowStream>) -> Self {
        Self {
            input_stream,
            last_chunk_number: 0,
        }
    }
}

impl Parser for ParquetParser {
    /// In the chunk case, we got an entire file in `data` and parse it immediately.
    fn parse(&mut self, data: &[u8]) -> (Option<Box<dyn InputBuffer>>, Vec<ParseError>) {
        let bytes = Bytes::copy_from_slice(data);

        let parquet_reader = match ParquetRecordBatchReader::try_new(bytes, 1_000_000) {
            Ok(parquet_reader) => parquet_reader,
            Err(e) => {
                return (
                    None,
                    vec![ParseError::bin_envelope_error(
                        format!("error parsing Parquet data: {e}"),
                        &[],
                        None,
                    )],
                );
            }
        };

        let mut errors = Vec::new();
        for batch in parquet_reader {
            match batch {
                Ok(batch) => {
                    if let Err(e) = self.input_stream.insert(&batch) {
                        errors.push(ParseError::bin_envelope_error(
                            format!(
                                "error parsing parquet data (chunk {}): {e}",
                                self.last_chunk_number
                            ),
                            &[],
                            None,
                        ));
                    }
                }
                Err(e) => {
                    errors.push(ParseError::bin_envelope_error(
                        format!(
                            "error extracting parquet data (chunk {}): {e}",
                            self.last_chunk_number
                        ),
                        &[],
                        None,
                    ));
                }
            }

            self.last_chunk_number += 1;
        }

        (self.input_stream.take_all(), errors)
    }

    fn fork(&self) -> Box<dyn Parser> {
        Box::new(Self::new(self.input_stream.fork()))
    }

    fn splitter(&self) -> Box<dyn super::Splitter> {
        Box::new(Sponge)
    }
}

/// Parquet format encoder.
pub struct ParquetOutputFormat;

impl OutputFormat for ParquetOutputFormat {
    fn name(&self) -> Cow<'static, str> {
        Cow::Borrowed("parquet")
    }

    fn config_from_http_request(
        &self,
        endpoint_name: &str,
        request: &HttpRequest,
    ) -> Result<Box<dyn ErasedSerialize>, ControllerError> {
        Ok(Box::new(
            ParquetEncoderConfig::deserialize(UrlDeserializer::new(form_urlencoded::parse(
                request.query_string().as_bytes(),
            )))
            .map_err(|e| {
                ControllerError::encoder_config_parse_error(
                    endpoint_name,
                    &e,
                    request.query_string(),
                )
            })?,
        ))
    }

    fn new_encoder(
        &self,
        endpoint_name: &str,
        config: &ConnectorConfig,
        key_schema: &Option<Relation>,
        value_schema: &Relation,
        consumer: Box<dyn OutputConsumer>,
    ) -> Result<Box<dyn Encoder>, ControllerError> {
        if key_schema.is_some() {
            return Err(ControllerError::invalid_encoder_configuration(
                endpoint_name,
                "Parquet encoder cannot be attached to an index",
            ));
        }

        if matches!(
            config.transport,
            feldera_types::config::TransportConfig::RedisOutput(_)
        ) {
            return Err(ControllerError::invalid_encoder_configuration(
                endpoint_name,
                "'parquet' format not supported with Redis connector",
            ));
        }

        let config = ParquetEncoderConfig::deserialize(&config.format.as_ref().unwrap().config)
            .map_err(|e| {
                ControllerError::encoder_config_parse_error(
                    endpoint_name,
                    &e,
                    &serde_yaml::to_string(&config).unwrap_or_default(),
                )
            })?;
        Ok(Box::new(ParquetEncoder::new(
            consumer,
            config,
            value_schema.clone(),
        )?))
    }
}

pub fn relation_to_arrow_fields(fields: &[Field], delta_lake: bool) -> Vec<ArrowField> {
    fn field_to_arrow_field(f: &Field, delta_lake: bool) -> ArrowField {
        ArrowField::new(
            &f.name,
            columntype_to_datatype(&f.columntype, delta_lake),
            // FIXME: Databricks refuses to understand the `nullable: false` constraint.
            delta_lake || f.columntype.nullable,
        )
    }

    fn struct_to_arrow_fields(fields: &[Field], delta_lake: bool) -> Fields {
        Fields::from(
            fields
                .iter()
                .map(|f| field_to_arrow_field(f, delta_lake))
                .collect::<Vec<ArrowField>>(),
        )
    }

    // The type conversion is chosen in accordance with our internal
    // data types (see sqllib). This may need to be adjusted in the future
    // or made configurable.
    fn columntype_to_datatype(c: &ColumnType, delta_lake: bool) -> DataType {
        match c.typ {
            SqlType::Boolean => DataType::Boolean,
            SqlType::TinyInt => DataType::Int8,
            SqlType::SmallInt => DataType::Int16,
            SqlType::Int => DataType::Int32,
            SqlType::BigInt => DataType::Int64,
            SqlType::Real => DataType::Float32,
            SqlType::Double => DataType::Float64,
            SqlType::Decimal => DataType::Decimal128(
                c.precision.unwrap_or(0).try_into().unwrap(),
                c.scale.unwrap_or(0).try_into().unwrap(),
            ),
            SqlType::Char | SqlType::Varchar => DataType::Utf8,
            SqlType::Time => DataType::Time64(TimeUnit::Nanosecond),
            // DeltaLake only supports microsecond-based timestamp encoding, so we just
            // hardwire that for now.  We can make it configurable in the future.
            // FIXME: Also, the timezone should be `None`, but that gets compiled into `timezone_ntz`
            // in the JSON schema, which Databricks doesn't fully support yet.
            SqlType::Timestamp => DataType::Timestamp(
                TimeUnit::Microsecond,
                if delta_lake { Some("UTC".into()) } else { None },
            ),
            SqlType::Date => DataType::Date32,
            SqlType::Null => DataType::Null,
            // Today all supported connectors happen to use string encoding for UUID.
            // In the future, we will have connectors thay use byte array representation,
            // notably Iceberg. We will need to make this mapping configurable to support
            // such connectors.
            SqlType::Uuid => DataType::Utf8,
            SqlType::Binary => DataType::LargeBinary,
            SqlType::Varbinary => DataType::LargeBinary,
            SqlType::Interval(
                IntervalUnit::YearToMonth | IntervalUnit::Year | IntervalUnit::Month,
            ) => DataType::Interval(ArrowIntervalUnit::YearMonth),
            // We serialize variants into JSON strings.
            SqlType::Variant => DataType::Utf8,
            SqlType::Interval(_) => DataType::Interval(ArrowIntervalUnit::DayTime),
            SqlType::Array => {
                // SqlType::Array implies c.component.is_some()
                let array_component = c.component.as_ref().unwrap();
                DataType::LargeList(Arc::new(ArrowField::new_list_field(
                    columntype_to_datatype(array_component, delta_lake),
                    // FIXME: Databricks refuses to understand the `nullable: false` constraint.
                    delta_lake || array_component.nullable,
                )))
            }
            SqlType::Struct => DataType::Struct(struct_to_arrow_fields(
                c.fields.as_ref().unwrap(),
                delta_lake,
            )),
            SqlType::Map => {
                let key_type = c.key.as_ref().unwrap();
                let val_type = c.value.as_ref().unwrap();

                DataType::Map(
                    Arc::new(ArrowField::new_struct(
                        "entries",
                        [
                            Arc::new(ArrowField::new(
                                "key",
                                columntype_to_datatype(key_type, delta_lake),
                                key_type.nullable,
                            )),
                            Arc::new(ArrowField::new(
                                "value",
                                columntype_to_datatype(val_type, delta_lake),
                                val_type.nullable,
                            )),
                        ]
                        .as_slice(),
                        false,
                    )),
                    false,
                )
            }
        }
    }

    fields
        .iter()
        .map(|f| field_to_arrow_field(f, delta_lake))
        .collect::<Vec<ArrowField>>()
}

pub fn relation_to_parquet_schema(
    fields: &[Field],
    delta_lake: bool,
) -> Result<SerdeArrowSchema, ControllerError> {
    let fields = relation_to_arrow_fields(fields, delta_lake);

    SerdeArrowSchema::try_from(fields.as_slice()).map_err(|e| ControllerError::SchemaParseError {
        error: format!("Unable to convert schema to parquet/arrow: {e}"),
    })
}

struct ParquetEncoder {
    /// Input handle to push serialized data to.
    output_consumer: Box<dyn OutputConsumer>,
    _relation: Relation,
    parquet_schema: SerdeArrowSchema,
    config: ParquetEncoderConfig,
    buffer: Vec<u8>,
    max_buffer_size: usize,
}

impl ParquetEncoder {
    fn new(
        output_consumer: Box<dyn OutputConsumer>,
        config: ParquetEncoderConfig,
        _relation: Relation,
    ) -> Result<Self, ControllerError> {
        let max_buffer_size = output_consumer.max_buffer_size_bytes();
        Ok(Self {
            output_consumer,
            config,
            parquet_schema: relation_to_parquet_schema(&_relation.fields, false)?,
            _relation,
            buffer: Vec::new(),
            max_buffer_size,
        })
    }
}

impl Encoder for ParquetEncoder {
    fn consumer(&mut self) -> &mut dyn OutputConsumer {
        self.output_consumer.as_mut()
    }

    fn encode(&mut self, batch: &dyn SerBatchReader) -> AnyResult<()> {
        let mut buffer = take(&mut self.buffer);
        let props = WriterProperties::builder().build();
        let fields =
            <Vec<FieldRef> as TryFrom<SerdeArrowSchema>>::try_from(self.parquet_schema.clone())?;
        let schema = Arc::new(Schema::new(fields));

        let mut builder = ArrayBuilder::new(self.parquet_schema.clone())?;

        let mut num_records = 0;
        let mut cursor = CursorWithPolarity::new(
            // TODO: make this configurable instead of using the default.
            batch.cursor(RecordFormat::Parquet(default_arrow_serde_config().clone()))?,
        );
        while cursor.key_valid() {
            if !cursor.val_valid() {
                cursor.step_key();
                continue;
            }
            let mut w = cursor.weight();
            if !(-MAX_DUPLICATES..=MAX_DUPLICATES).contains(&w) {
                bail!("Unable to output record with very large weight {w}. Consider adjusting your SQL queries to avoid duplicate output records, e.g., using 'SELECT DISTINCT'.");
            }
            if w < 0 {
                panic!("Deletes for the parquet format are not yet supported.");
            }

            while w != 0 {
                let prev_len = buffer.len();
                cursor.serialize_key_to_arrow(&mut builder)?;

                // TODO: buffer.len() is always 0 here atm:
                let buffer_full = buffer.len() > self.max_buffer_size;
                if buffer_full {
                    if num_records == 0 {
                        // We should be able to fit at least one record in the buffer.
                        bail!("Parquet record exceeds maximum buffer size supported by the output transport. Max supported buffer size is {} bytes, but the record requires {} bytes.",
                                  self.max_buffer_size,
                                  buffer.len() - prev_len);
                    }
                    buffer.truncate(prev_len);
                } else {
                    if w > 0 {
                        w -= 1;
                    } else {
                        w += 1;
                    }
                    num_records += 1;
                }

                if num_records >= self.config.buffer_size_records || buffer_full {
                    let buffer_cursor = Cursor::new(&mut buffer);
                    let mut writer =
                        ArrowWriter::try_new(buffer_cursor, schema.clone(), Some(props.clone()))?;
                    let batch = builder.to_record_batch()?;
                    writer.write(&batch)?;
                    writer.close()?;

                    self.output_consumer.push_buffer(&buffer, num_records);
                    buffer.clear();

                    num_records = 0;
                }
            }
            cursor.step_key();
        }

        if num_records > 0 {
            let buffer_cursor = Cursor::new(&mut buffer);
            let mut writer =
                ArrowWriter::try_new(buffer_cursor, schema.clone(), Some(props.clone()))?;
            let batch = builder.to_record_batch()?;
            writer.write(&batch)?;
            writer.close()?;
            self.output_consumer.push_buffer(&buffer, num_records);
            buffer.clear();
        }

        self.buffer = buffer;
        Ok(())
    }
}
