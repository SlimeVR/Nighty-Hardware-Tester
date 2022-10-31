pub struct LogCtx {
    indent: usize,
}

impl LogCtx {
    pub fn new() -> LogCtx {
        LogCtx { indent: 0 }
    }

    fn log(&self, level: &str, msg: &str) {
        println!("{} > {}{}", level, " ".repeat(self.indent), msg);
    }

    pub fn enter(&mut self, msg: &str) {
        self.log(">>>", msg);
        self.indent += 2;
    }

    pub fn leave(&mut self, msg: &str) {
        self.indent -= 2;
        self.log("<<<", msg);
    }

    pub fn inf(&self, msg: &str) {
        self.log("INF", msg);
    }

    pub fn wrn(&self, msg: &str) {
        self.log("WRN", msg);
    }

    pub fn err(&self, msg: &str) {
        self.log("ERR", msg);
    }

    pub fn ftl(&self, msg: &str) {
        self.log("FTL", msg);
    }

    pub fn dbg(&self, msg: &str) {
        self.log("DBG", msg);
    }

    pub fn trc(&self, msg: &str) {
        self.log("TRC", msg);
    }
}
