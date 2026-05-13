use anyhow::Result;
use clap::{Parser, Subcommand};
use std::path::PathBuf;
use wayfinder_cli::{run_command, Command};

#[derive(Parser)]
#[command(name = "wayfinder")]
#[command(about = "Static quality checker for Wayfinder Guild resources")]
#[command(version)]
struct Cli {
    #[command(subcommand)]
    command: CliCommand,
}

#[derive(Subcommand)]
enum CliCommand {
    /// Run all linters and print a project summary.
    Doctor {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Validate src/main/resources/skills/**/SKILL.md.
    LintSkills {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Validate src/main/resources/rpg/*.json.
    LintRpg {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Validate evals/travel-cases.json.
    LintEvals {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Validate src/main/resources/prompts/rpg/*.st.
    LintPrompts {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Validate src/main/resources/document/*.md.
    LintRagDocs {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Validate Wayfinder naming governance rules.
    LintNaming {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
    /// Print Wayfinder resource statistics.
    Summary {
        #[arg(long, default_value = ".")]
        workspace: PathBuf,
    },
}

fn main() -> Result<()> {
    let cli = Cli::parse();
    let command = match cli.command {
        CliCommand::Doctor { workspace } => Command::Doctor { workspace },
        CliCommand::LintSkills { workspace } => Command::LintSkills { workspace },
        CliCommand::LintRpg { workspace } => Command::LintRpg { workspace },
        CliCommand::LintEvals { workspace } => Command::LintEvals { workspace },
        CliCommand::LintPrompts { workspace } => Command::LintPrompts { workspace },
        CliCommand::LintRagDocs { workspace } => Command::LintRagDocs { workspace },
        CliCommand::LintNaming { workspace } => Command::LintNaming { workspace },
        CliCommand::Summary { workspace } => Command::Summary { workspace },
    };
    run_command(command)
}
