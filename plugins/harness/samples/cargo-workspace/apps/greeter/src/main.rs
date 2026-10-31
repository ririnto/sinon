fn main() {
    let name = std::env::args().nth(1).unwrap_or_default();
    println!("{}", greeting::greet(&name));
}
