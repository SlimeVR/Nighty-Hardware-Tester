use colored::Colorize;

pub fn write(msg: &str, color: colored::Color) {
	println!("{}", msg.color(color));
}

pub fn error(msg: &str) {
	write(&format!("❌ {}", msg), colored::Color::Red);
}

pub fn success(msg: &str) {
	write(&format!("✅ {}", msg), colored::Color::Green);
}

pub fn in_progress(msg: &str) {
	write(&format!("🕑 {}", msg), colored::Color::BrightBlue);
}

pub fn action(msg: &str) {
	write(&format!("❗ {}", msg), colored::Color::BrightYellow);
}

pub fn banner(msg: &str, color: colored::Color) {
	println!("{}", msg.color(color));
	println!("{}", msg.color(color));
	println!("{}", msg.color(color));
	println!("{}", msg.color(color));
	println!("{}", msg.color(color));
}

pub fn debug(msg: &str) {
	write(&format!("🐛 {}", msg), colored::Color::BrightBlack);
}

pub fn debug_no_ln(msg: &str) {
	print!("{}", msg.color(colored::Color::BrightBlack));
}
