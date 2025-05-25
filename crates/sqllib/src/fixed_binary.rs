//! optimized implementation of a few byte arrays for fixed sizes that
//! are multiples of 64 bits (8 bytes)

use crate::{ByteArray, SqlResult, SqlRuntimeError};
use base64::prelude::*;
use dbsp::NumEntries;
use feldera_types::serde_with_context::{
    serde_config::BinaryFormat, DeserializeWithContext, SerializeWithContext, SqlSerdeConfig,
};
use num::Zero;
use serde::{
    de::{Error as _, Visitor},
    Deserialize, Deserializer, Serialize, Serializer,
};
use size_of::SizeOf;
use std::{borrow::Cow, fmt::Debug};

// TODO: Use a macro to generate this code for multiple values of SIZE_IN_BITS

const SIZE_IN_BITS: usize = 256;
const SIZE_IN_BYTES: usize = SIZE_IN_BITS / 8;
const SIZE_IN_U64: usize = SIZE_IN_BITS / 64;

/// A ByteArray object, representing a SQL value with type
/// `BINARY(N)` where N is a multiple of 8.
#[derive(
    Debug,
    Clone,
    PartialEq,
    Eq,
    PartialOrd,
    Ord,
    Hash,
    Serialize,
    SizeOf,
    rkyv::Archive,
    rkyv::Serialize,
    rkyv::Deserialize,
)]
#[archive_attr(derive(Ord, Eq, PartialEq, PartialOrd))]
#[archive(compare(PartialEq, PartialOrd))]
#[serde(transparent)]
pub struct FixedByteArray {
    data: [u64; SIZE_IN_U64],
}

impl Default for FixedByteArray {
    fn default() -> Self {
        Self::zero(SIZE_IN_BYTES)
    }
}

fn slice_to_array<T, const N: usize>(slice: &[T]) -> SqlResult<[T; N]>
where
    T: Zero + Copy,
{
    if slice.len() != N {
        Err(SqlRuntimeError::from_string(format!(
            "Slice size {} does not match expected size {}",
            slice.len(),
            N
        )))
    } else {
        let mut arr = [T::zero(); N];
        arr.copy_from_slice(slice);
        Ok(arr)
    }
}

impl SerializeWithContext<SqlSerdeConfig> for FixedByteArray {
    fn serialize_with_context<S>(
        &self,
        serializer: S,
        context: &SqlSerdeConfig,
    ) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let ba = self.to_byte_array();
        ba.serialize_with_context(serializer, context)
    }
}

struct ByteVisitor;

impl Visitor<'_> for ByteVisitor {
    type Value = FixedByteArray;

    fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
        formatter.write_str("byte array")
    }

    fn visit_bytes<E>(self, v: &[u8]) -> Result<Self::Value, E>
    where
        E: serde::de::Error,
    {
        Ok(FixedByteArray::from_bytes(v))
    }
}

impl<'de> DeserializeWithContext<'de, SqlSerdeConfig> for FixedByteArray {
    fn deserialize_with_context<D>(
        deserializer: D,
        config: &'de SqlSerdeConfig,
    ) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        match config.binary_format {
            BinaryFormat::Array => {
                let data = Vec::<u8>::deserialize(deserializer)?;
                Ok(Self::from_bytes(&data))
            }
            BinaryFormat::Base64 => {
                let str: Cow<'de, str> = Deserialize::deserialize(deserializer)?;
                let data = BASE64_STANDARD
                    .decode(&*str)
                    .map_err(|e| D::Error::custom(format!("invalid base64 string: {e}")))?;
                Ok(Self::from_bytes(&data))
            }
            BinaryFormat::Bytes => deserializer.deserialize_bytes(ByteVisitor),
            BinaryFormat::PgHex => Err(D::Error::custom(
                "binary format Postgres Hexadecimal is not supported for input",
            )),
        }
    }
}

#[doc(hidden)]
impl NumEntries for &FixedByteArray {
    const CONST_NUM_ENTRIES: Option<usize> = Some(SIZE_IN_U64);

    #[doc(hidden)]
    #[inline]
    fn num_entries_shallow(&self) -> usize {
        SIZE_IN_U64
    }

    #[doc(hidden)]
    #[inline]
    fn num_entries_deep(&self) -> usize {
        SIZE_IN_U64
    }
}

impl From<&[u8]> for FixedByteArray {
    fn from(value: &[u8]) -> Self {
        Self::from_bytes(value)
    }
}

impl FixedByteArray {
    /// Create a FixedByteArray from a slice of bytes
    pub fn new(d: [u64; SIZE_IN_U64]) -> Self {
        Self { data: d }
    }

    /// Create a FixedByteArray from a slice of bytes
    pub fn from_bytes(d: &[u8]) -> Self {
        let vec = d
            .chunks_exact(8)
            .map(|chunk| u64::from_le_bytes(slice_to_array(chunk).unwrap()))
            .collect::<Vec<u64>>();
        Self::new(slice_to_array(&vec).unwrap())
    }

    pub fn zero(size: usize) -> Self {
        if size != SIZE_IN_BYTES {
            panic!("Size mismatch");
        }
        Self {
            data: [0u64; SIZE_IN_U64],
        }
    }

    /// Create a ByteArray from a Vector of u64
    pub fn from_vec(d: Vec<u64>) -> Self {
        Self {
            data: slice_to_array(&d[0..SIZE_IN_U64]).unwrap(),
        }
    }

    /// Length of the byte array in bytes
    pub fn length(&self) -> usize {
        SIZE_IN_BYTES
    }

    #[doc(hidden)]
    /// Combine two byte arrays of the same length using
    /// a pointwise function.  panics if the lengths
    /// are not the same.
    pub fn zip<F>(&self, other: &Self, op: F) -> Self
    where
        F: Fn(&u64, &u64) -> u64,
    {
        let result: Vec<u64> = self
            .data
            .iter()
            .zip(other.data.iter())
            .map(|(l, r)| op(l, r))
            .collect();
        FixedByteArray::from_vec(result)
    }

    #[doc(hidden)]
    /// Bytewise 'and' of two byte arrays of the same length.
    /// Panics if the arrays do not have the same length.
    pub fn and(&self, other: &Self) -> Self {
        self.zip(other, |left, right| left & right)
    }

    #[doc(hidden)]
    /// Bytewise 'or' of two byte arrays of the same length.
    /// Panics if the arrays do not have the same length.
    pub fn or(&self, other: &Self) -> Self {
        self.zip(other, |left, right| left | right)
    }

    #[doc(hidden)]
    /// Bytewise 'xor' of two byte arrays of the same length.
    /// Panics if the arrays do not have the same length.
    pub fn xor(&self, other: &Self) -> Self {
        self.zip(other, |left, right| left ^ right)
    }

    /// Get a reference to the data in a FixedByteArray as an u64 slice
    pub fn as_slice(&self) -> &[u64] {
        &self.data
    }

    pub fn to_byte_array(&self) -> ByteArray {
        let vec: &Vec<u8> = &self.data.iter().flat_map(|&d| d.to_le_bytes()).collect();
        ByteArray::from_vec(vec.clone())
    }
}
