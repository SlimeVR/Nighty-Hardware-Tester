use colored::Colorize;
use std::sync;

enum Event {
    ReportEvent(ReportEvent),
    ResetReport,
}

enum ReportEvent {
    Success(String),
    Warn(String),
    Error(String),
}

pub struct TUI {
    rx: sync::mpsc::Receiver<Event>,
    tx: sync::mpsc::Sender<Event>,
}

pub struct Renderer {
    rx: sync::mpsc::Receiver<Event>,
    report_events: Vec<ReportEvent>,
}

pub struct Reporter {
    tx: sync::mpsc::Sender<Event>,
}

impl TUI {
    pub fn new() -> Result<TUI, Box<dyn std::error::Error>> {
        let (tx, rx) = sync::mpsc::channel::<Event>();

        Ok(TUI { rx, tx })
    }

    pub fn split(self) -> (Renderer, Reporter) {
        (
            Renderer {
                rx: self.rx,
                report_events: Vec::new(),
            },
            Reporter { tx: self.tx },
        )
    }
}

impl Renderer {
    fn draw(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        crossterm::execute!(
            std::io::stdout(),
            crossterm::terminal::Clear(crossterm::terminal::ClearType::All)
        )?;

        let lines = self
            .report_events
            .iter()
            .map(|e| {
                let (style, s) = match &e {
                    ReportEvent::Success(s) => (colored::Color::Green, s),
                    ReportEvent::Warn(s) => (colored::Color::Yellow, s),
                    ReportEvent::Error(s) => (colored::Color::Red, s),
                };

                format!("{}", s.color(style))
            })
            .collect::<Vec<String>>()
            .join("\n");

        crossterm::queue!(std::io::stdout(), crossterm::cursor::MoveTo(0, 0))?;
        // crossterm::queue!(std::io::stdout(), crossterm::style::Print(lines))?;

        println!("{}", lines);

        Ok(())
    }

    pub fn run(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        loop {
            match self.rx.recv()? {
                Event::ReportEvent(msg) => {
                    self.report_events.push(msg);
                    self.draw()?;
                }
                Event::ResetReport => {
                    self.report_events.clear();
                    self.draw()?;

                    println!("---- RESET! ----");
                }
            }
        }
    }
}

impl Reporter {
    pub fn success(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Success(msg.to_string())))
            .unwrap();

        // println!(">>>> Sent success: {}", msg.to_string());
    }

    pub fn warn(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Warn(msg.to_string())))
            .unwrap();

        // println!(">>>> Sent warn: {}", msg.to_string());
    }

    pub fn error(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Error(msg.to_string())))
            .unwrap();

        // println!(">>>> Sent error: {}", msg.to_string());
    }

    pub fn reset(&mut self) {
        self.tx.send(Event::ResetReport).unwrap();
    }
}
