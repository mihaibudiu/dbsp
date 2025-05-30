use core::cmp::Ordering;
use dbsp::{
    algebra::{HasOne, MulByRef, ZRingValue, ZSet},
    trace::{cursor::Cursor, BatchReader},
    DBData, ZWeight,
};

use dbsp::dynamic::{DowncastTrait, Erase};
use feldera_sqllib::{SqlString, WSet, Weight};
use sltsqlvalue::*;
use std::ops::{Add, Neg};
use std::sync::Arc;

#[derive(Eq, PartialEq)]
pub enum SortOrder {
    NONE,
    ROW,
    VALUE,
}

fn compare<T>(left: &Vec<T>, right: &Vec<T>) -> Ordering
where
    T: Ord,
{
    let llen = left.len();
    let rlen = right.len();
    let min = llen.min(rlen);
    for i in 0..min {
        let cmp = left[i].cmp(&right[i]);
        if cmp != Ordering::Equal {
            return cmp;
        }
    }
    llen.cmp(&rlen)
}

/// Convert a zset to a vector of SqlRow.
/// Elements with > 1 weights will generate multiple SqlRows
/// # Panics
/// if any of the zset weights is negative
pub fn zset_to_rows<K>(set: &WSet<K>) -> Vec<SqlRow>
where
    K: DBData + ToSqlRow,
{
    let mut w: ZWeight = 0;
    set.weighted_count(w.erase_mut());

    let mut result = Vec::with_capacity(w.try_into().unwrap());
    let mut cursor = set.cursor();
    while cursor.key_valid() {
        let mut w = **cursor.weight();
        if !w.ge0() {
            panic!("Negative weight in output set!");
        }
        while !w.le0() {
            let row_vec = unsafe { cursor.key().downcast::<K>() }.to_row();
            result.push(row_vec);
            w = w.add(Weight::neg(Weight::one()));
        }
        cursor.step_key();
    }
    result
}

struct DataRows<'a> {
    rows: Vec<Vec<SqlString>>,
    order: &'a SortOrder,
    format: &'a SqlString,
}

impl<'a> DataRows<'a> {
    pub fn new(format: &'a SqlString, order: &'a SortOrder) -> Self {
        Self {
            rows: Vec::new(),
            order,
            format,
        }
    }
    pub fn with_capacity(format: &'a SqlString, order: &'a SortOrder, capacity: usize) -> Self {
        Self {
            rows: Vec::with_capacity(capacity),
            order,
            format,
        }
    }
    pub fn push(&mut self, sql_row: SqlRow) {
        let row_vec = sql_row.to_slt_strings(self.format.str());
        if *self.order == SortOrder::ROW || *self.order == SortOrder::NONE {
            self.rows.push(row_vec);
        } else if *self.order == SortOrder::VALUE {
            for r in row_vec {
                self.rows.push(vec![r])
            }
        }
    }

    pub fn get(mut self) -> Vec<Vec<SqlString>> {
        if *self.order != SortOrder::NONE {
            self.rows.sort_unstable_by(&compare);
        }
        self.rows
    }
}

/// The format is from the SqlLogicTest query output string format
pub fn zset_to_strings<K>(set: &WSet<K>, format: SqlString, order: SortOrder) -> Vec<Vec<SqlString>>
where
    K: DBData + ToSqlRow,
{
    let rows = zset_to_rows(set);
    let mut data_rows = DataRows::with_capacity(&format, &order, rows.len());
    for row in rows {
        data_rows.push(row)
    }
    data_rows.get()
}

/// Version of hash that takes the result of orderby: a zset that is expected
/// to contain a single vector with all the data.
pub fn zset_of_vectors_to_strings<K>(
    set: &WSet<Arc<Vec<K>>>,
    format: SqlString,
    order: SortOrder,
) -> Vec<Vec<SqlString>>
where
    K: DBData + ToSqlRow,
{
    let mut data_rows = DataRows::new(&format, &order);
    let mut cursor = (*set).cursor();
    while cursor.key_valid() {
        let w = **cursor.weight();
        if w != Weight::one() {
            panic!("Weight is not one!");
        }
        let row_vec: &Arc<Vec<K>> = unsafe { cursor.key().downcast::<Arc<Vec<K>>>() };
        let row_vec = (*row_vec).to_vec();
        let sql_rows = row_vec.iter().map(|k| k.to_row());
        for row in sql_rows {
            data_rows.push(row);
        }
        cursor.step_key();
    }
    data_rows.get()
}

/// This function mimics the md5 checksum computation from SqlLogicTest
/// The format is from the SqlLogicTest query output string format
pub fn hash<K>(set: &WSet<K>, format: SqlString, order: SortOrder) -> SqlString
where
    K: DBData + ToSqlRow,
{
    let vec = zset_to_strings::<K>(set, format, order);
    let mut builder = String::default();
    for row in vec {
        for col in row {
            builder = builder + &col.str() + "\n"
        }
    }
    // println!("{}", builder);
    let digest = md5::compute(builder);
    SqlString::from(format!("{:x}", digest))
}

/// Version of hash that takes the result of orderby: a zset that is expected
/// to contain a single vector with all the data.
pub fn hash_vectors<K>(set: &WSet<Arc<Vec<K>>>, format: SqlString, order: SortOrder) -> SqlString
where
    K: DBData + ToSqlRow,
{
    // Result of orderby - there should be at most one row in the set.
    let mut builder = String::default();
    let mut cursor = (*set).cursor();
    while cursor.key_valid() {
        let w = **cursor.weight();
        if w != Weight::one() {
            panic!("Weight is not one!");
        }
        let row_vec: &Arc<Vec<K>> = unsafe { cursor.key().downcast::<Arc<Vec<K>>>() };
        let row_vec = (*row_vec).to_vec();
        let sql_rows = row_vec.iter().map(|k| k.to_row());
        let mut data_rows = DataRows::with_capacity(&format, &order, sql_rows.len());
        for row in sql_rows {
            data_rows.push(row);
        }
        for row in data_rows.get() {
            for col in row {
                builder = builder + &col.str() + "\n"
            }
        }
        cursor.step_key();
    }
    // println!("{}", builder);
    let digest = md5::compute(builder);
    SqlString::from_ref(&format!("{:x}", digest))
}

// The count of elements in a zset that contains a vector is
// given by the count of the elements of the vector times the
// weight of the vector.
pub fn weighted_vector_count<K>(set: &WSet<Vec<K>>) -> isize
where
    K: DBData + ToSqlRow,
{
    let mut sum: isize = 0;
    let mut cursor = set.cursor();
    while cursor.key_valid() {
        let key = unsafe { cursor.key().downcast::<Vec<K>>() };
        sum += (key.len() as isize).mul_by_ref(&**cursor.weight());
        cursor.step_key();
    }
    sum
}
