use std::io;
use std::sync;
use std::time;

use crossterm::event;
use crossterm::terminal;
use tui::backend;
use tui::layout;
use tui::style;
use tui::widgets;

enum Event {
    Tick,
    Log(String),
    Input(crossterm::event::KeyEvent),
}

pub struct TUI {
    terminal: tui::terminal::Terminal<backend::CrosstermBackend<io::Stdout>>,
    rx: sync::mpsc::Receiver<Event>,
    tx: sync::mpsc::Sender<Event>,
}

pub struct Renderer {
    rx: sync::mpsc::Receiver<Event>,
    terminal: tui::terminal::Terminal<backend::CrosstermBackend<io::Stdout>>,

    log: Vec<String>,
}

pub struct InputReader {
    tx: sync::mpsc::Sender<Event>,
}

pub struct Ticker {
    tx: sync::mpsc::Sender<Event>,
    tick_rate: time::Duration,
}

impl TUI {
    pub fn new() -> Result<TUI, Box<dyn std::error::Error>> {
        let (tx, rx) = sync::mpsc::channel::<Event>();

        let stdout = io::stdout();
        let backend = backend::CrosstermBackend::new(stdout);
        let terminal = tui::Terminal::new(backend)?;

        Ok(TUI { terminal, rx, tx })
    }

    pub fn split(self) -> (Renderer, InputReader, Ticker) {
        (
            Renderer {
                rx: self.rx,
                terminal: self.terminal,
                log: Vec::new(),
            },
            InputReader {
                tx: self.tx.clone(),
            },
            Ticker {
                tx: self.tx.clone(),
                tick_rate: time::Duration::from_millis(100),
            },
        )
    }
}

impl Renderer {
    fn draw(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        self.terminal.draw(|f| {
            let size = f.size();
            let chunks = layout::Layout::default()
                .direction(layout::Direction::Vertical)
                .margin(1)
                .constraints([
                    layout::Constraint::Length(1),
                    layout::Constraint::Min(2),
                    layout::Constraint::Length(1),
                ])
                .split(size);

            let title = widgets::Paragraph::new("SlimeVR Board Tester")
                .style(
                    style::Style::default()
                        .fg(style::Color::Yellow)
                        .add_modifier(style::Modifier::BOLD),
                )
                .alignment(layout::Alignment::Center);

            let timestamp = widgets::Paragraph::new(chrono::Utc::now().to_rfc3339().to_string())
                .style(style::Style::default().fg(style::Color::White))
                .alignment(layout::Alignment::Center);

            let log = widgets::List::new(
                self.log
                    .iter()
                    .map(|s| widgets::ListItem::new(s.to_owned()))
                    .collect::<Vec<_>>(),
            )
            .block(
                widgets::Block::default()
                    .borders(widgets::Borders::ALL)
                    .border_type(widgets::BorderType::Rounded),
            )
            .style(style::Style::default().fg(style::Color::White))
            .highlight_style(style::Style::default().fg(style::Color::Yellow))
            .highlight_symbol("> ");

            f.render_widget(title, chunks[0]);
            f.render_widget(timestamp, chunks[2]);
            f.render_stateful_widget(log, chunks[1], &mut widgets::ListState::default());
        })?;

        Ok(())
    }

    pub fn run(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        terminal::enable_raw_mode()?;

        self.terminal.clear()?;

        loop {
            match self.rx.recv()? {
                Event::Tick => self.draw()?,
                Event::Log(msg) => {
                    self.log.push(msg);
                    self.draw()?;
                }
                Event::Input(key) => match key.code {
                    event::KeyCode::Char('q') | event::KeyCode::Esc => {
                        terminal::disable_raw_mode()?;
                        break Ok(());
                    }
                    _ => {}
                },
            }
        }
    }
}

impl InputReader {
    pub fn run(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        loop {
            if let crossterm::event::Event::Key(key) = crossterm::event::read()? {
                self.tx.send(Event::Input(key))?;
            }
        }
    }
}

impl Ticker {
    pub fn run(&mut self) -> Result<(), Box<dyn std::error::Error>> {
        loop {
            self.tx.send(Event::Tick)?;
            self.tx.send(Event::Log("Tick".to_string()))?;
            std::thread::sleep(self.tick_rate);
        }
    }
}
