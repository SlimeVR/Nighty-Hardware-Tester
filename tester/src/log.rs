use colored::Colorize;

pub struct LogCtx {
    stack: Vec<String>,
}

impl LogCtx {
    pub fn new() -> LogCtx {
        LogCtx { stack: vec![] }
    }

    fn log(&self, level: colored::ColoredString, msg: colored::ColoredString) {
        let stack = if self.stack.len() > 0 {
            "[".to_string() + &self.stack.join("::") + "]"
        } else {
            "".to_string()
        }
        .bright_black();

        println!("{: <5} {: <40} {}", level, msg, stack,);
    }

    pub fn enter(&mut self, stage: &str) {
        self.stack.push(stage.to_string());
        self.log("-->".green(), format!("Entering stage: {}", stage).white());
    }

    pub fn leave(&mut self) {
        let stage = self.stack.pop().unwrap();
        self.log("<--".red(), format!("Leaving stage: {}", stage).white());
    }

    pub fn info(&self, msg: &str) {
        self.log("INFO".green(), msg.bright_white());
    }

    pub fn warn(&self, msg: &str) {
        self.log("WARN".yellow(), msg.yellow());
    }

    pub fn error(&self, msg: &str) {
        self.log("ERROR".red(), msg.red());
    }

    pub fn fatal(&self, msg: &str) {
        self.log("FATAL".bright_red(), msg.bright_red());
    }

    pub fn debug(&self, msg: &str) {
        self.log("DEBUG".bright_black(), msg.bright_black());
    }

    pub fn trace(&self, msg: &str) {
        self.log("TRACE".bright_black(), msg.bright_yellow());
    }
}
