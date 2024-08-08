// === Features ===
#![feature(const_trait_impl)]
#![feature(string_remove_matches)]
#![feature(once_cell_try)]



mod prelude {
    pub use derive_more::*;
    pub use enso_build_base::prelude::*;

    pub use convert_case::Case;
    pub use convert_case::Casing;
    pub use itertools::Itertools;
    pub use proc_macro2::Span;
    pub use proc_macro2::TokenStream;
    pub use quote::quote;
    pub use syn::Data;
    pub use syn::DeriveInput;
    pub use syn::Ident;
}

pub mod paths;
pub mod program_args;
