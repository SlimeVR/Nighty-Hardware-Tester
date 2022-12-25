use colored::Colorize;
use std::sync;

enum Event {
    ReportEvent(ReportEvent),
    ResetReport,
}

enum ReportEvent {
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
        crossterm::execute!(std::io::stdout(), crossterm::cursor::MoveTo(0, 0))?;

        let (columns, rows) = crossterm::terminal::size().unwrap();

        let lines = self
            .report_events
            .iter()
            .map(|e| match &e {
                ReportEvent::Success(s) => Some((colored::Color::Green, s)),
                ReportEvent::Error(s) => Some((colored::Color::Red, s)),
                ReportEvent::InProgress(s) => Some((colored::Color::White, s)),
                ReportEvent::Action(s) => Some((colored::Color::BrightBlue, s)),
                ReportEvent::Fill(_) => None,
            })
            .filter_map(|e| e)
            .map(|(color, s)| format!("{}", s.color(color)))
            .collect::<Vec<String>>();

        println!("{}", lines.join("\n"));

        if let Some(ReportEvent::Fill(color)) = self.report_events.last() {
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
                Event::ReportEvent(msg) => {
                    match msg {
                        ReportEvent::InProgress(_) => {
                            self.report_events.push(msg);
                        }
                        _ => {
                            if let Some(last_event) = self.report_events.last() {
                                if let ReportEvent::InProgress(_) = last_event {
                                    self.report_events.pop();
                                }
                            }

                            self.report_events.push(msg);
                        }
                    }

                    self.draw()?;
                }
                Event::ResetReport => {
                    self.report_events.clear();
                    self.draw()?;
                }
            }
        }
    }
}

impl Reporter {
    pub fn success(&mut self, msg: &str) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Success(
                "✓ ".to_string() + msg,
            )))
            .unwrap();
    }

    pub fn error(&mut self, msg: &str) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Error(
                "╳ ".to_string() + msg,
            )))
            .unwrap();
    }

    pub fn in_progress(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::InProgress(msg.to_string())))
            .unwrap();
    }

    pub fn action(&mut self, msg: impl ToString) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Action(msg.to_string())))
            .unwrap();
    }

    pub fn fill(&mut self, color: Color) {
        self.tx
            .send(Event::ReportEvent(ReportEvent::Fill(color)))
            .unwrap();
    }

    pub fn reset(&mut self) {
        self.tx.send(Event::ResetReport).unwrap();
    }
}
