use rustls::crypto::CryptoProvider;

mod auth;
#[cfg(test)]
#[cfg(feature = "integration-test")]
mod integration_test;

pub mod api;
pub mod compiler;
pub mod config;
pub mod db;
pub mod db_notifier;
pub mod demo;
pub mod error;
pub mod local_runner;
pub mod logging;
pub mod metrics;
pub mod pipeline_automata;
pub mod probe;
pub mod retries;
pub mod runner;

/// Some dependencies of this crate use the `rustls` library. This library has two features
/// `ring` and `aws-lc-rs`. When both are enabled, the library requires a process-wide default
/// crypto provider to be configured. While no single dependency enables both these features,
/// Rust's feature unification may end up enabling both of them, depending on the exact rustls
/// dependency versions in use.
///
/// Bottom line: this function must be called in the `main` function in this crate, as well as
/// in all tests that exercise libraries that use `rustls` internally.
pub fn ensure_default_crypto_provider() {
    let _ = CryptoProvider::install_default(rustls::crypto::aws_lc_rs::default_provider());
}