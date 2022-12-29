use colored::Colorize;
use std::sync::mpsc;

enum Event {
    LogEvent(LogEvent),
    ResetRenderer,
}

enum LogEvent {
    Success(String),
    Error(String),
    InProgress(String),
    Action(String),
    Fill(Color),
}

pub enum Color {
    Green,
    Red,
}
pub struct LoggerBuilder {}

pub struct Renderer {
    rx: mpsc::Receiver<Event>,
    events: Vec<LogEvent>,
}

pub struct Logger {
    tx: mpsc::Sender<Event>,
}

impl LoggerBuilder {
    pub fn split() -> (Renderer, Logger) {
        let (tx, rx) = mpsc::channel::<Event>();

        (
            Renderer {
                rx,
                events: Vec::new(),
            },
            Logger { tx },
        )
    }
}

impl Renderer {
    fn draw(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        crossterm::execute!(
            std::io::stdout(),
            crossterm::terminal::Clear(crossterm::terminal::ClearType::All)
        )?;
        crossterm::execute!(std::io::stdout(), crossterm::cursor::MoveTo(0, 0))?;

        let (columns, rows) = crossterm::terminal::size().unwrap();

        let lines = self
            .events
            .iter()
            .map(|e| match &e {
                LogEvent::Success(s) => Some((colored::Color::Green, s)),
                LogEvent::Error(s) => Some((colored::Color::Red, s)),
                LogEvent::InProgress(s) => Some((colored::Color::White, s)),
                LogEvent::Action(s) => Some((colored::Color::BrightBlue, s)),
                LogEvent::Fill(_) => None,
            })
            .filter_map(|e| e)
            .map(|(color, s)| format!("{}", s.color(color)))
            .collect::<Vec<String>>();

        println!("{}", lines.join("\n"));

        if let Some(LogEvent::Fill(color)) = self.events.last() {
            let color = match color {
                Color::Green => colored::Color::Green,
                Color::Red => colored::Color::Red,
            };

            let s = "X".repeat(columns as usize);

            let rows = rows as usize - lines.len() - 1;
            for _ in 0..rows {
                println!("{}", s.on_color(color).color(color));
            }
        }

        Ok(())
    }

    pub fn run(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        loop {
            match self.rx.recv()? {
                Event::LogEvent(msg) => {
                    match msg {
                        LogEvent::InProgress(_) => {
                            self.events.push(msg);
                        }
                        _ => {
                            if let Some(last_event) = self.events.last() {
                                if let LogEvent::InProgress(_) = last_event {
                                    self.events.pop();
                                }
                            }

                            self.events.push(msg);
                        }
                    }

                    self.draw()?;
                }
                Event::ResetRenderer => {
                    self.events.clear();
                    self.draw()?;
                }
            }
        }
    }
}

impl Logger {
    pub fn success(&mut self, msg: &str) {
        self.tx
            .send(Event::LogEvent(LogEvent::Success("✓ ".to_string() + msg)))
            .unwrap();
    }

    pub fn error(&mut self, msg: &str) {
        self.tx
            .send(Event::LogEvent(LogEvent::Error("╳ ".to_string() + msg)))
            .unwrap();
    }

    pub fn in_progress(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::LogEvent(LogEvent::InProgress(msg.to_string())))
            .unwrap();
    }

    pub fn action(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::LogEvent(LogEvent::Action(msg.to_string())))
            .unwrap();
    }

    pub fn fill(&mut self, color: Color) {
        self.tx
            .send(Event::LogEvent(LogEvent::Fill(color)))
            .unwrap();
    }

    pub fn reset(&mut self) {
        self.tx.send(Event::ResetRenderer).unwrap();
    }
}
