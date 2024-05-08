//! SqlValue is a dynamically-typed object that can represent a subset
//! of the Tuple* values in a SQL program.  This is used by SQL Logic Test,
//! which has a particular way of formatting tuples.  The
//! Tuple* types are used for computations, and they are converted
//! to SqlRow objects when they need to be serialized as strings.

#![allow(non_snake_case)]

use crate::{
    binary::ByteArray,
    Date,
    DefaultOptSemigroup,
    LongInterval, Semigroup, ShortInterval,
    Time, Timestamp,
};
use core::cmp::Ordering;
use core::ops::{Add, AddAssign};
use dbsp::{
    algebra::{AddAssignByRef, AddByRef, HasZero, F32, F64, MulByRef, Present, ZRingValue},
    trace::Serializer, trace::Deserializer,
};
use rust_decimal::Decimal;
use pipeline_types::{deserialize_without_context, serialize_without_context};
use serde::{Deserialize, Serialize};
use size_of::*;
use std::{
    ops::Index,
    vec::Vec,
};
use rkyv::Archive;

///////////////////////////// SqlValue /////////////////////////////////////

#[derive(
    Clone,
    Debug,
    Default,
    Deserialize,
    Eq,
    Hash,
    PartialEq,
    PartialOrd,
    Ord,
    Serialize,
    //rkyv::Serialize,
    //rkyv::Deserialize,
)]
// #[archive_attr(derive(Ord, Eq, PartialEq, PartialOrd))]
// #[archive(compare(PartialEq, PartialOrd))]
pub enum SqlValue {
    // Used for the NULL value with the NULL type
    #[default]
    Empty,

    Bool(bool),
    I8(i8),
    I16(i16),
    I32(i32),
    I64(i64),
    F32(F32),
    F64(F64),
    Decimal(Decimal),
    Str(String),
    Time(Time),
    Timestamp(Timestamp),
    Date(Date),
    ByteArray(ByteArray),
    ShortInterval(ShortInterval),
    LongInterval(LongInterval),
    Vec(Vec<SqlValue>),
    Tuple(SqlTuple),

    OptBool(Option<bool>),
    OptI8(Option<i8>),
    OptI16(Option<i16>),
    OptI32(Option<i32>),
    OptI64(Option<i64>),
    OptF32(Option<F32>),
    OptF64(Option<F64>),
    OptDecimal(Option<Decimal>),
    OptStr(Option<String>),
    OptTime(Option<Time>),
    OptTimestamp(Option<Timestamp>),
    OptDate(Option<Date>),
    OptByteArray(Option<ByteArray>),
    OptShortInterval(Option<ShortInterval>),
    OptLongInterval(Option<LongInterval>),
    OptVec(Option<Vec<SqlValue>>),
    OptTuple(Option<SqlTuple>),
}

impl SizeOf for SqlValue {
    fn size_of_children(&self, context: &mut size_of::Context) {
        match self {
            SqlValue::Vec(data) => data.iter()
                .for_each(|element| element.size_of_children(context)),
            SqlValue::OptVec(None) => (),
            SqlValue::OptVec(Some(data)) => data.iter()
                .for_each(|element| element.size_of_children(context)),
            _ => (),
        }
    }
}

impl Archive for SqlValue {
    type Archived = ();
    type Resolver = ();

    // Required method
    unsafe fn resolve(
        &self,
        _pos: usize,
        _resolver: Self::Resolver,
        _out: *mut Self::Archived
    ) {
        todo!();
    }
}

impl PartialEq<()> for SqlValue {
    fn eq(&self, _other: &()) -> bool {
        false
    }
}

impl PartialEq<SqlValue> for () {
    fn eq(&self, _other: &SqlValue) -> bool {
        false
    }
}

impl PartialOrd<()> for SqlValue {
    fn partial_cmp(&self, _other: &()) -> Option<Ordering> {
        Some(Ordering::Greater)
    }
}

impl PartialOrd<SqlValue> for () {
    fn partial_cmp(&self, _other: &SqlValue) -> Option<Ordering> {
        Some(Ordering::Less)
    }
}

impl rkyv::Serialize<Serializer> for SqlValue {
    fn serialize(
        &self,
        _serializer: &mut Serializer,
    ) -> Result<Self::Resolver, <Serializer as rkyv::Fallible>::Error> {
        todo!()
    }
}

impl rkyv::Deserialize<SqlValue, Deserializer> for () {
    fn deserialize(
        &self,
        _deserializer: &mut Deserializer,
    ) -> Result<SqlValue, <Deserializer as rkyv::Fallible>::Error> {
        todo!()
    }
}

impl Semigroup<SqlValue> for DefaultOptSemigroup<SqlValue>
{
    fn combine(left: &SqlValue, right: &SqlValue) -> SqlValue {
        match (left, right) {
            (SqlValue::I8(left),           SqlValue::I8(right)        ) => SqlValue::I8(left.checked_add(*right)
                                                                                        .expect("Addition overflow")),
            (SqlValue::I16(left),          SqlValue::I16(right)       ) => SqlValue::I16(left.checked_add(*right)
                                                                                         .expect("Addition overflow")),
            (SqlValue::I32(left),          SqlValue::I32(right)       ) => SqlValue::I32(left.checked_add(*right)
                                                                                         .expect("Addition overflow")),
            (SqlValue::I64(left),          SqlValue::I64(right)       ) => SqlValue::I64(left.checked_add(*right)
                                                                                         .expect("Addition overflow")),
            (SqlValue::F32(left),          SqlValue::F32(right)       ) => SqlValue::F32(left + right),
            (SqlValue::F64(left),          SqlValue::F64(right)       ) => SqlValue::F64(left + right),
            (SqlValue::Decimal(left),      SqlValue::Decimal(right)   ) => SqlValue::Decimal(left.add(right)),
            (SqlValue::OptI8(None),        SqlValue::OptI8(_)         ) => right.clone(),
            (SqlValue::OptI8(_),           SqlValue::OptI8(None)      ) => left.clone(),
            (SqlValue::OptI8(Some(left)),  SqlValue::OptI8(Some(right))) => SqlValue::OptI8(Some(
                left.checked_add(*right)
                    .expect("Addition overflow"))),
            (SqlValue::OptI16(None),       SqlValue::OptI16(_)        ) => right.clone(),
            (SqlValue::OptI16(_),          SqlValue::OptI16(None)     ) => left.clone(),
            (SqlValue::OptI16(Some(left)), SqlValue::OptI16(Some(right))) => SqlValue::OptI16(Some(
                left.checked_add(*right)
                    .expect("Addition overflow"))),
            (SqlValue::OptI32(None),       SqlValue::OptI32(_)        ) => right.clone(),
            (SqlValue::OptI32(_),          SqlValue::OptI32(None)     ) => left.clone(),
            (SqlValue::OptI32(Some(left)), SqlValue::OptI32(Some(right))) => SqlValue::OptI32(Some(
                left.checked_add(*right)
                    .expect("Addition overflow"))),
            (SqlValue::OptI64(None),       SqlValue::OptI64(_)        ) => right.clone(),
            (SqlValue::OptI64(_),          SqlValue::OptI64(None)     ) => left.clone(),
            (SqlValue::OptI64(Some(left)), SqlValue::OptI64(Some(right))) => SqlValue::OptI64(Some(
                left.checked_add(*right)
                    .expect("Addition overflow"))),
            (SqlValue::OptF32(None),       SqlValue::OptF32(_)        ) => right.clone(),
            (SqlValue::OptF32(_),          SqlValue::OptF32(None)     ) => left.clone(),
            (SqlValue::OptF32(Some(left)), SqlValue::OptF32(Some(right))) => SqlValue::OptF32(Some(left + right)),
            (SqlValue::OptF64(None),       SqlValue::OptF64(_)        ) => right.clone(),
            (SqlValue::OptF64(_),          SqlValue::OptF64(None)     ) => left.clone(),
            (SqlValue::OptF64(Some(left)), SqlValue::OptF64(Some(right))) => SqlValue::OptF64(Some(left + right)),
            (SqlValue::OptDecimal(None),       SqlValue::OptDecimal(_)          ) => right.clone(),
            (SqlValue::OptDecimal(_),          SqlValue::OptDecimal(None)       ) => left.clone(),
            (SqlValue::OptDecimal(Some(left)), SqlValue::OptDecimal(Some(right))) => SqlValue::OptDecimal(Some(left.add(right))),
            (_, _) => panic!("Unexpected Semigroup operation on {:?}, {:?}", left, right),
        }
    }
}

serialize_without_context!(SqlValue);
deserialize_without_context!(SqlValue);

macro_rules! make_froms {
    /*
    Example generated code:

    impl From<i8> for SqlValue {
        fn from(value: i8) -> Self {
            SqlValue::I8(value)
        }
    }

    impl From<SqlValue> for i8 {
        #[inline]
        fn from(value: SqlValue) -> Self {
            match value {
                SqlValue::I8(value) => value,
                _ => unreachable!("{:?} type is not 'i8'", value),
            }
        }
    }

    impl From<Option<i8>> for SqlValue {
        fn from(value: Option<i8>) -> Self {
            SqlValue::OptI8(value)
        }
    }

    impl From<SqlValue> for Option<i8> {
        #[inline]
        fn from(value: SqlValue) -> Self {
            match value {
                SqlValue::OptI8(value) => value,
                _ => unreachable!("{:?} type is not 'Option<i8>'", value),
            }
        }
    }
    */
    (
        $type: ty, $enum: tt
    ) => {
        ::paste::paste! {
            impl From<$type> for SqlValue {
                #[inline]
                fn from(value: $type) -> Self {
                    SqlValue::$enum(value)
                }
            }

            impl From<SqlValue> for $type {
                #[inline]
                fn from(value: SqlValue) -> Self {
                    match value {
                        SqlValue::$enum(value) => value,
                        _ => unreachable!(concat!("{:?} type is not '", stringify!($ty), "'"), value),
                    }
                }
            }

            impl From<Option<$type>> for SqlValue {
                #[inline]
                fn from(value: Option<$type>) -> Self {
                    SqlValue::[< Opt $enum >](value)
                }
            }

            impl From<SqlValue> for Option<$type> {
                #[inline]
                fn from(value: SqlValue) -> Self {
                    match value {
                        SqlValue::[< Opt $enum >](value) => value,
                        _ => unreachable!(concat!("{:?} type is not 'Option<", stringify!($ty), ">'"), value),
                    }
                }
            }
        }
    };
}

make_froms!(bool, Bool);
make_froms!(i8, I8);
make_froms!(i16, I16);
make_froms!(i32, I32);
make_froms!(i64, I64);
make_froms!(F32, F32);
make_froms!(F64, F64);
make_froms!(Decimal, Decimal);
make_froms!(String, Str);
make_froms!(Time, Time);
make_froms!(Timestamp, Timestamp);
make_froms!(Date, Date);
make_froms!(ByteArray, ByteArray);
make_froms!(ShortInterval, ShortInterval);
make_froms!(LongInterval, LongInterval);
make_froms!(SqlTuple, Tuple);

/////////////////////////////////////// MonoidSqlValue //////////////////////////////////

// A SqlValue that implements arithmetic, used in aggregations
#[derive(
    Clone,
    Debug,
    Default,
    Deserialize,
    Eq,
    Hash,
    PartialEq,
    Ord,
    Serialize,
    SizeOf,
    rkyv::Archive,
    rkyv::Serialize,
    rkyv::Deserialize,
)]
#[archive_attr(derive(Ord, Eq, PartialEq, PartialOrd))]
#[archive(compare(PartialEq, PartialOrd))]
pub enum MonoidSqlValue {
    // First value is a zero of any type
    #[default]
    Zero,
    I8(i8),
    I16(i16),
    I32(i32),
    I64(i64),
    F32(F32),
    F64(F64),
    Decimal(Decimal),
}

static MONOID_SQL_VALUE_ZERO: MonoidSqlValue = MonoidSqlValue::Zero;

impl PartialOrd for MonoidSqlValue {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        match (self, other) {
            // zero types against zero
            (MonoidSqlValue::Zero, MonoidSqlValue::Zero)       => Some(Ordering::Equal),
            (MonoidSqlValue::Zero, MonoidSqlValue::I8(right))  => 0i8.partial_cmp(right),
            (MonoidSqlValue::Zero, MonoidSqlValue::I16(right)) => 0i16.partial_cmp(right),
            (MonoidSqlValue::Zero, MonoidSqlValue::I32(right)) => 0i32.partial_cmp(right),
            (MonoidSqlValue::Zero, MonoidSqlValue::I64(right)) => 0i64.partial_cmp(right),
            (MonoidSqlValue::Zero, MonoidSqlValue::F32(right)) => F32::zero().partial_cmp(right),
            (MonoidSqlValue::Zero, MonoidSqlValue::F64(right)) => F64::zero().partial_cmp(right),
            (MonoidSqlValue::Zero, MonoidSqlValue::Decimal(right)) => Decimal::zero().partial_cmp(right),
            (MonoidSqlValue::I8(left),  MonoidSqlValue::Zero)  => left.partial_cmp(&0i8),
            (MonoidSqlValue::I16(left), MonoidSqlValue::Zero)  => left.partial_cmp(&0i16),
            (MonoidSqlValue::I32(left), MonoidSqlValue::Zero)  => left.partial_cmp(&0i32),
            (MonoidSqlValue::I64(left), MonoidSqlValue::Zero)  => left.partial_cmp(&0i64),
            (MonoidSqlValue::F32(left), MonoidSqlValue::Zero)  => left.partial_cmp(&F32::zero()),
            (MonoidSqlValue::F64(left), MonoidSqlValue::Zero)  => left.partial_cmp(&F64::zero()),
            (MonoidSqlValue::Decimal(left), MonoidSqlValue::Zero) => left.partial_cmp(&Decimal::zero()),

            // zero types with self
            (MonoidSqlValue::I8(left),  MonoidSqlValue::I8(right)) => left.partial_cmp(right),
            (MonoidSqlValue::I16(left), MonoidSqlValue::I16(right)) => left.partial_cmp(right),
            (MonoidSqlValue::I32(left), MonoidSqlValue::I32(right)) => left.partial_cmp(right),
            (MonoidSqlValue::I64(left), MonoidSqlValue::I64(right)) => left.partial_cmp(right),
            (MonoidSqlValue::F32(left), MonoidSqlValue::F32(right)) => left.partial_cmp(right),
            (MonoidSqlValue::F64(left), MonoidSqlValue::F64(right)) => left.partial_cmp(right),
            (MonoidSqlValue::Decimal(left), MonoidSqlValue::Decimal(right)) => left.partial_cmp(right),
            (_, _) => None,
        }
    }
}

serialize_without_context!(MonoidSqlValue);
deserialize_without_context!(MonoidSqlValue);

macro_rules! make_froms_monoid {
    (
        $type: ty, $enum: tt, $zero: expr
    ) => {
        ::paste::paste! {
            impl From<$type> for MonoidSqlValue {
                #[inline]
                fn from(value: $type) -> Self {
                    if (value == $zero) {
                        MonoidSqlValue::Zero
                    } else {
                        MonoidSqlValue::$enum(value)
                    }
                }
            }

            impl From<MonoidSqlValue> for $type {
                #[inline]
                fn from(value: MonoidSqlValue) -> Self {
                    match value {
                        MonoidSqlValue::Zero => $zero,
                        MonoidSqlValue::$enum(value) => value,
                        _ => unreachable!(concat!("{:?} type is not '", stringify!($ty), "'"), value),
                    }
                }
            }
        }
    };
}

make_froms_monoid!(i8, I8, 0i8);
make_froms_monoid!(i16, I16, 0i16);
make_froms_monoid!(i32, I32, 0i32);
make_froms_monoid!(i64, I64, 0i64);
make_froms_monoid!(F32, F32, F32::zero());
make_froms_monoid!(F64, F64, F64::zero());
make_froms_monoid!(Decimal, Decimal, Decimal::zero());

macro_rules! make_mul {
    (
        $type: ty
    ) => {
        impl MulByRef<$type> for MonoidSqlValue {
            type Output = Self;
            fn mul_by_ref(&self, other: &$type) -> Self::Output {
                match self {
                    MonoidSqlValue::Zero => MonoidSqlValue::Zero,
                    MonoidSqlValue::I8(value)  => MonoidSqlValue::I8(value.mul_by_ref(other)),
                    MonoidSqlValue::I16(value) => MonoidSqlValue::I16(value.mul_by_ref(other)),
                    MonoidSqlValue::I32(value) => MonoidSqlValue::I32(value.mul_by_ref(other)),
                    MonoidSqlValue::I64(value) => MonoidSqlValue::I64(value.mul_by_ref(other)),
                    MonoidSqlValue::F32(value) => MonoidSqlValue::F32(value.mul_by_ref(other)),
                    MonoidSqlValue::F64(value) => MonoidSqlValue::F64(value.mul_by_ref(other)),
                    MonoidSqlValue::Decimal(value) => MonoidSqlValue::Decimal(value.mul_by_ref(other)),
                }
            }
        }
    }
}

make_mul!(Present);
make_mul!(i64);
make_mul!(i32);
make_mul!(isize);

impl HasZero for MonoidSqlValue {
    fn is_zero(&self) -> bool {
        match self {
            MonoidSqlValue::Zero => true,
            _ => false,
        }
    }

    fn zero() -> Self {
        MonoidSqlValue::Zero
    }
}

impl Add<MonoidSqlValue> for MonoidSqlValue {
    type Output = Self;

    fn add(self, rhs: MonoidSqlValue) -> Self::Output {
        match (&self, &rhs) {
            (&MonoidSqlValue::Zero, _) => rhs,
            (_, &MonoidSqlValue::Zero) => self,
            (&MonoidSqlValue::I8(left),  &MonoidSqlValue::I8(right)) => MonoidSqlValue::I8(left + right),
            (&MonoidSqlValue::I16(left), &MonoidSqlValue::I16(right)) => MonoidSqlValue::I16(left + right),
            (&MonoidSqlValue::I32(left), &MonoidSqlValue::I32(right)) => MonoidSqlValue::I32(left + right),
            (&MonoidSqlValue::I64(left), &MonoidSqlValue::I64(right)) => MonoidSqlValue::I64(left + right),
            (&MonoidSqlValue::F32(left), &MonoidSqlValue::F32(right)) => MonoidSqlValue::F32(left + right),
            (&MonoidSqlValue::F64(left), &MonoidSqlValue::F64(right)) => MonoidSqlValue::F64(left + right),
            (&MonoidSqlValue::Decimal(left), &MonoidSqlValue::Decimal(right)) => MonoidSqlValue::Decimal(left + right),
            (_, _) => panic!("Mismatched values in add {:?} + {:?}", self, rhs),
        }
    }
}

impl AddByRef<MonoidSqlValue> for MonoidSqlValue {
    fn add_by_ref(&self, rhs: &MonoidSqlValue) -> MonoidSqlValue {
        match (&self, &rhs) {
            (&MonoidSqlValue::Zero, _) => rhs.clone(),
            (_, &MonoidSqlValue::Zero) => self.clone(),
            (&MonoidSqlValue::I8(left),  &MonoidSqlValue::I8(right)) => MonoidSqlValue::I8(left + right),
            (&MonoidSqlValue::I16(left), &MonoidSqlValue::I16(right)) => MonoidSqlValue::I16(left + right),
            (&MonoidSqlValue::I32(left), &MonoidSqlValue::I32(right)) => MonoidSqlValue::I32(left + right),
            (&MonoidSqlValue::I64(left), &MonoidSqlValue::I64(right)) => MonoidSqlValue::I64(left + right),
            (&MonoidSqlValue::F32(left), &MonoidSqlValue::F32(right)) => MonoidSqlValue::F32(left + right),
            (&MonoidSqlValue::F64(left), &MonoidSqlValue::F64(right)) => MonoidSqlValue::F64(left + right),
            (&MonoidSqlValue::Decimal(left), &MonoidSqlValue::Decimal(right)) => MonoidSqlValue::Decimal(left + right),
            (_, _) => panic!("Mismatched values in add_by_ref {:?} + {:?}", self, rhs),
        }
    }
}

impl AddAssign<MonoidSqlValue> for MonoidSqlValue {
    fn add_assign(&mut self, rhs: MonoidSqlValue) {
        match (&self, &rhs) {
            (&MonoidSqlValue::Zero, _) => *self = rhs.clone(),
            (_, &MonoidSqlValue::Zero) => (),
            (MonoidSqlValue::I8(left),  &MonoidSqlValue::I8(right)) =>  *self = MonoidSqlValue::I8 (*left + right),
            (MonoidSqlValue::I16(left), &MonoidSqlValue::I16(right)) => *self = MonoidSqlValue::I16(*left + right),
            (MonoidSqlValue::I32(left), &MonoidSqlValue::I32(right)) => *self = MonoidSqlValue::I32(*left + right),
            (MonoidSqlValue::I64(left), &MonoidSqlValue::I64(right)) => *self = MonoidSqlValue::I64(*left + right),
            (MonoidSqlValue::F32(left), &MonoidSqlValue::F32(right)) => *self = MonoidSqlValue::F32(*left + right),
            (MonoidSqlValue::F64(left), &MonoidSqlValue::F64(right)) => *self = MonoidSqlValue::F64(*left + right),
            (MonoidSqlValue::Decimal(left), &MonoidSqlValue::Decimal(right)) => *self = MonoidSqlValue::Decimal(*left + right),
            (_, _) => panic!("Mismatched values in add_assign {:?} + {:?}", self, rhs),
        }
    }
}

impl AddAssignByRef<MonoidSqlValue> for MonoidSqlValue {
    fn add_assign_by_ref(&mut self, rhs: &MonoidSqlValue) {
        print!("{:?}+{:?}=", self, rhs);
        match (&self, &rhs) {
            (&MonoidSqlValue::Zero, _) => *self = rhs.clone(),
            (_, &MonoidSqlValue::Zero) => (),
            (MonoidSqlValue::I8(left),  &MonoidSqlValue::I8(right)) =>  *self = MonoidSqlValue::I8 (*left + right),
            (MonoidSqlValue::I16(left), &MonoidSqlValue::I16(right)) => *self = MonoidSqlValue::I16(*left + right),
            (MonoidSqlValue::I32(left), &MonoidSqlValue::I32(right)) => *self = MonoidSqlValue::I32(*left + right),
            (MonoidSqlValue::I64(left), &MonoidSqlValue::I64(right)) => *self = MonoidSqlValue::I64(*left + right),
            (MonoidSqlValue::F32(left), &MonoidSqlValue::F32(right)) => *self = MonoidSqlValue::F32(*left + right),
            (MonoidSqlValue::F64(left), &MonoidSqlValue::F64(right)) => *self = MonoidSqlValue::F64(*left + right),
            (MonoidSqlValue::Decimal(left), &MonoidSqlValue::Decimal(right)) => *self = MonoidSqlValue::Decimal(*left + right),
            (_, _) => panic!("Mismatched values in add_assign_by_ref {:?} + {:?}", self, rhs),
        }
        println!("{:?}", self);
    }
}

//////////////////////////////////////////// SqlTuple /////////////////////////////////////

#[derive(
    Clone,
    Debug,
    Default,
    Deserialize,
    Eq,
    Hash,
    PartialEq,
    PartialOrd,
    Serialize,
    rkyv::Archive,
    rkyv::Serialize,
    rkyv::Deserialize,
    Ord
)]
#[archive_attr(derive(Ord, Eq, PartialEq, PartialOrd))]
#[archive(compare(PartialEq, PartialOrd))]
pub struct SqlTuple {
    values: Vec<SqlValue>,
}

/*
impl rkyv::Archive for SqlTuple {
    type Resolver = ();
    type Archived = ();

    // Required method
    unsafe fn resolve(
        &self,
        _pos: usize,
        _resolver: Self::Resolver,
        _out: *mut Self::Archived
    ) {
        todo!();
    }
}
*/

impl SizeOf for SqlTuple {
    #[inline]
    fn size_of_children(&self, context: &mut Context) {
        self.values.size_of_children(context)
    }
}

impl SqlTuple {
    pub fn from_vec(values: Vec<SqlValue>) -> Self {
        Self {
            values: values
        }
    }

    pub fn default() -> Self {
        Self::from_vec(Vec::default())
    }

    pub fn len(&self) -> usize {
        self.values.len()
    }

    pub fn from0() -> Self {
        Self::from_vec(vec!())
    }

    pub fn from1(value: SqlValue) -> Self {
        Self::from_vec(vec!(value))
    }

    pub fn from2(value0: SqlValue, value1: SqlValue) -> Self {
        Self::from_vec(vec!(value0, value1))
    }

    pub fn from3(value0: SqlValue, value1: SqlValue,
                 value2: SqlValue,
    ) -> Self {
        Self::from_vec(vec!(value0, value1, value2))
    }

    pub fn from4(value0: SqlValue, value1: SqlValue,
                 value2: SqlValue, value3: SqlValue,
    ) -> Self {
        Self::from_vec(vec!(value0, value1, value2, value3))
    }

    pub fn from5(value0: SqlValue, value1: SqlValue,
                 value2: SqlValue, value3: SqlValue,
                 value4: SqlValue,
    ) -> Self {
        Self::from_vec(vec!(value0, value1, value2, value3, value4))
    }

    pub fn from6(value0: SqlValue, value1: SqlValue,
                 value2: SqlValue, value3: SqlValue,
                 value4: SqlValue, value5: SqlValue,
    ) -> Self {
        Self::from_vec(vec!(value0, value1, value2, value3, value4, value5))
    }
}

impl Index<usize> for SqlTuple {
    type Output = SqlValue;

    fn index<'a>(&'a self, i: usize) -> &'a SqlValue {
        &self.values[i]
    }
}

//////////////////////////////////// MonoidSqlTuple ///////////////////////////////////////

#[derive(
    Clone,
    Debug,
    Default,
    Deserialize,
    Eq,
    Hash,
    PartialEq,
    PartialOrd,
    Serialize,
    rkyv::Archive,
    rkyv::Serialize,
    rkyv::Deserialize,
    Ord
)]
#[archive_attr(derive(Ord, Eq, PartialEq, PartialOrd))]
#[archive(compare(PartialEq, PartialOrd))]
pub enum MonoidSqlTuple {
    #[default]
    Zero,
    Vector(Vec<MonoidSqlValue>),
}

impl SizeOf for MonoidSqlTuple {
    #[inline]
    fn size_of_children(&self, context: &mut Context) {
        match self {
            MonoidSqlTuple::Zero => (),
            MonoidSqlTuple::Vector(values) => values.size_of_children(context),
        }
    }
}

impl MonoidSqlTuple {
    pub fn from_vec(values: Vec<MonoidSqlValue>) -> Self {
        MonoidSqlTuple::Vector(values)
    }

    pub fn from1(value: MonoidSqlValue) -> Self {
        Self::from_vec(vec!(value))
    }
}

impl Index<usize> for MonoidSqlTuple {
    type Output = MonoidSqlValue;

    fn index<'a>(&'a self, i: usize) -> &'a MonoidSqlValue {
        match self {
            MonoidSqlTuple::Zero => &MONOID_SQL_VALUE_ZERO,
            MonoidSqlTuple::Vector(values) => &values[i],
        }
    }
}

impl<W> MulByRef<W> for MonoidSqlTuple
where
    W: ZRingValue,
    MonoidSqlValue: MulByRef<W, Output=MonoidSqlValue>,
{
    type Output = Self;
    fn mul_by_ref(&self, other: &W) -> Self::Output {
        match self {
            MonoidSqlTuple::Zero => MonoidSqlTuple::Zero,
            MonoidSqlTuple::Vector(values) => Self::from_vec(values.clone().into_iter()
                                                             .map(|value| value.mul_by_ref(other))
                                                             .collect()),
        }
    }
}

impl HasZero for MonoidSqlTuple {
    fn is_zero(&self) -> bool {
        match self {
            MonoidSqlTuple::Zero => true,
            MonoidSqlTuple::Vector(values) => values.iter().all(|value| value.is_zero()),
        }
    }

    fn zero() -> Self {
        MonoidSqlTuple::Zero
    }
}

impl Add<MonoidSqlTuple> for MonoidSqlTuple {
    type Output = Self;

    fn add(self, rhs: MonoidSqlTuple) -> Self::Output {
        match (&self, &rhs) {
            (&MonoidSqlTuple::Zero, _) => rhs,
            (_, &MonoidSqlTuple::Zero) => self.clone(),
            (&MonoidSqlTuple::Vector(ref left), &MonoidSqlTuple::Vector(ref right)) =>
                MonoidSqlTuple::Vector(left.iter().zip(right.iter()).map(move |(l, r)| l.clone().add(r.clone())).collect()),
        }
    }
}

impl AddByRef<MonoidSqlTuple> for MonoidSqlTuple {
    fn add_by_ref(&self, rhs: &MonoidSqlTuple) -> MonoidSqlTuple {
        match (&self, &rhs) {
            (&MonoidSqlTuple::Zero, _) => rhs.clone(),
            (_, &MonoidSqlTuple::Zero) => self.clone(),
            (&MonoidSqlTuple::Vector(ref left), &MonoidSqlTuple::Vector(ref right)) =>
                MonoidSqlTuple::Vector(left.iter().zip(right.iter()).map(move |(l, r)| l.add_by_ref(r)).collect()),
        }
    }
}

impl AddAssign<MonoidSqlTuple> for MonoidSqlTuple {
    fn add_assign(&mut self, rhs: MonoidSqlTuple) {
        let mut this = self;
        match (&mut this, &rhs) {
            (MonoidSqlTuple::Zero, _) => *this = rhs.clone(),
            (_, &MonoidSqlTuple::Zero) => (),
            (MonoidSqlTuple::Vector(left), &MonoidSqlTuple::Vector(ref right)) => {
                for (l, r) in left.iter_mut().zip(right.iter()) {
                    l.add_assign(r.clone());
                }
            }
        }
    }
}

impl AddAssignByRef<MonoidSqlTuple> for MonoidSqlTuple {
    fn add_assign_by_ref(&mut self, rhs: &MonoidSqlTuple) {
        let mut this = self;
        match (&mut this, rhs) {
            (MonoidSqlTuple::Zero, _) => *this = rhs.clone(),
            (_, &MonoidSqlTuple::Zero) => (),
            (MonoidSqlTuple::Vector(left), &MonoidSqlTuple::Vector(ref right)) => {
                println!("Case 3");
                for (l, r) in left.iter_mut().zip(right.iter()) {
                    l.add_assign_by_ref(r)
                }
            }
        }
    }
}
