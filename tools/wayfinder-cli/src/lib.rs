use anyhow::{Context, Result};
use serde_json::Value;
use std::{
    collections::{BTreeMap, BTreeSet},
    fs,
    path::{Path, PathBuf},
};
use walkdir::WalkDir;

pub enum Command {
    Doctor { workspace: PathBuf },
    LintSkills { workspace: PathBuf },
    LintRpg { workspace: PathBuf },
    LintEvals { workspace: PathBuf },
    LintPrompts { workspace: PathBuf },
    LintRagDocs { workspace: PathBuf },
    LintNaming { workspace: PathBuf },
    Summary { workspace: PathBuf },
}

#[derive(Debug, Default, Clone)]
pub struct CheckReport {
    pub name: &'static str,
    pub checked: usize,
    pub warnings: Vec<String>,
    pub errors: Vec<String>,
}

impl CheckReport {
    fn ok(name: &'static str, checked: usize) -> Self {
        Self {
            name,
            checked,
            warnings: Vec::new(),
            errors: Vec::new(),
        }
    }

    pub fn is_ok(&self) -> bool {
        self.errors.is_empty()
    }

    fn warn(&mut self, message: impl Into<String>) {
        self.warnings.push(message.into());
    }

    fn error(&mut self, message: impl Into<String>) {
        self.errors.push(message.into());
    }
}

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Summary {
    pub skills: usize,
    pub rpg_areas: usize,
    pub rpg_npcs: usize,
    pub rpg_projects: usize,
    pub rpg_skills: usize,
    pub rpg_modules: usize,
    pub eval_cases: usize,
    pub prompt_templates: usize,
    pub rag_docs: usize,
}

pub fn run_command(command: Command) -> Result<()> {
    match command {
        Command::Doctor { workspace } => {
            let reports = vec![
                lint_skills(&workspace)?,
                lint_rpg(&workspace)?,
                lint_evals(&workspace)?,
                lint_prompts(&workspace)?,
                lint_rag_docs(&workspace)?,
                lint_naming(&workspace)?,
            ];
            print_reports(&reports);
            print_summary(&collect_summary(&workspace)?);
            fail_on_errors(&reports)
        }
        Command::LintSkills { workspace } => {
            let report = lint_skills(&workspace)?;
            print_reports(&[report.clone()]);
            fail_on_errors(&[report])
        }
        Command::LintRpg { workspace } => {
            let report = lint_rpg(&workspace)?;
            print_reports(&[report.clone()]);
            fail_on_errors(&[report])
        }
        Command::LintEvals { workspace } => {
            let report = lint_evals(&workspace)?;
            print_reports(&[report.clone()]);
            fail_on_errors(&[report])
        }
        Command::LintPrompts { workspace } => {
            let report = lint_prompts(&workspace)?;
            print_reports(&[report.clone()]);
            fail_on_errors(&[report])
        }
        Command::LintRagDocs { workspace } => {
            let report = lint_rag_docs(&workspace)?;
            print_reports(&[report.clone()]);
            fail_on_errors(&[report])
        }
        Command::LintNaming { workspace } => {
            let report = lint_naming(&workspace)?;
            print_reports(&[report.clone()]);
            fail_on_errors(&[report])
        }
        Command::Summary { workspace } => {
            print_summary(&collect_summary(&workspace)?);
            Ok(())
        }
    }
}

pub fn lint_skills(workspace: &Path) -> Result<CheckReport> {
    let skills_root = workspace.join("src/main/resources/skills");
    let mut report = CheckReport::ok("skills", 0);
    if !skills_root.exists() {
        report.error(format!("missing directory {}", display(&skills_root)));
        return Ok(report);
    }

    for entry in WalkDir::new(&skills_root)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file() && entry.file_name() == "SKILL.md")
    {
        let path = entry.path();
        if path.parent() == Some(skills_root.as_path()) {
            continue;
        }

        report.checked += 1;
        let content =
            fs::read_to_string(path).with_context(|| format!("reading {}", display(path)))?;
        let Some(front_matter) = front_matter(&content) else {
            report.error(format!("{} missing YAML front matter", display(path)));
            continue;
        };
        let fields = parse_front_matter(front_matter);
        for key in ["id", "name", "description", "tags", "triggers", "priority"] {
            if fields
                .get(key)
                .map(|value| value.trim().is_empty())
                .unwrap_or(true)
            {
                report.error(format!(
                    "{} missing required field `{}`",
                    display(path),
                    key
                ));
            }
        }

        let dir_name = path
            .parent()
            .and_then(Path::file_name)
            .and_then(|name| name.to_str())
            .unwrap_or_default();
        if let Some(id) = fields.get("id") {
            if id.trim() != dir_name {
                report.error(format!(
                    "{} skill id `{}` does not match directory `{}`",
                    display(path),
                    id.trim(),
                    dir_name
                ));
            }
        }
        if let Some(priority) = fields.get("priority") {
            if priority.trim().parse::<i64>().is_err() {
                report.error(format!("{} priority must be numeric", display(path)));
            }
        }
    }

    if report.checked == 0 {
        report.warn("no concrete skill files found under skills/*/SKILL.md");
    }
    Ok(report)
}

pub fn lint_rpg(workspace: &Path) -> Result<CheckReport> {
    let rpg_root = workspace.join("src/main/resources/rpg");
    let mut report = CheckReport::ok("rpg", 0);
    let required = [
        "world.json",
        "projects.json",
        "skills.json",
        "modules.json",
        "profile.json",
    ];

    for file in required {
        let path = rpg_root.join(file);
        if !path.exists() {
            report.error(format!("missing {}", display(&path)));
        }
    }
    if !report.errors.is_empty() {
        return Ok(report);
    }

    let world = read_json(&rpg_root.join("world.json"))?;
    let projects = read_json(&rpg_root.join("projects.json"))?;
    let skills = read_json(&rpg_root.join("skills.json"))?;
    let modules = read_json(&rpg_root.join("modules.json"))?;
    let profile = read_json(&rpg_root.join("profile.json"))?;
    report.checked = 5;

    require_string(&mut report, "world.json", &world, "id");
    require_string(&mut report, "world.json", &world, "name");
    require_non_empty_array(&mut report, "world.json", &world, "areas");
    require_non_empty_array(&mut report, "world.json", &world, "npcs");
    require_non_empty_array(&mut report, "world.json", &world, "quickRoutes");
    require_array_root(&mut report, "projects.json", &projects);
    require_array_root(&mut report, "skills.json", &skills);
    require_array_root(&mut report, "modules.json", &modules);
    require_string(&mut report, "profile.json", &profile, "name");
    require_string(&mut report, "profile.json", &profile, "role");

    if let Some(routes) = world.get("quickRoutes").and_then(Value::as_array) {
        for route in routes {
            let id = string_field(route, "id").unwrap_or("<missing>");
            if string_field(route, "path").unwrap_or("").is_empty() {
                report.error(format!("world quickRoute `{}` has empty path", id));
            }
        }
    }

    let module_ids: BTreeSet<String> = modules
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|module| string_field(module, "id").map(str::to_owned))
        .collect();
    let area_module_refs = world
        .get("areas")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .flat_map(|area| string_array(area, "moduleIds"))
        .collect::<Vec<_>>();
    let npc_module_refs = world
        .get("npcs")
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .flat_map(|npc| string_array(npc, "moduleIds"))
        .collect::<Vec<_>>();

    for module_id in area_module_refs.into_iter().chain(npc_module_refs) {
        if !module_ids.contains(&module_id) {
            report.error(format!(
                "world references missing module id `{}`",
                module_id
            ));
        }
    }

    Ok(report)
}

pub fn lint_evals(workspace: &Path) -> Result<CheckReport> {
    let path = workspace.join("evals/travel-cases.json");
    let mut report = CheckReport::ok("evals", 0);
    if !path.exists() {
        report.error(format!("missing {}", display(&path)));
        return Ok(report);
    }
    let cases = read_json(&path)?;
    let Some(items) = cases.as_array() else {
        report.error("evals/travel-cases.json must be a JSON array");
        return Ok(report);
    };
    report.checked = items.len();
    for item in items {
        let id = string_field(item, "id").unwrap_or("<missing>");
        for field in ["id", "name", "input"] {
            if string_field(item, field).unwrap_or("").trim().is_empty() {
                report.error(format!("eval case `{}` missing `{}`", id, field));
            }
        }
        if !item
            .get("expectedSkills")
            .map(Value::is_array)
            .unwrap_or(false)
        {
            report.error(format!(
                "eval case `{}` expectedSkills must be an array",
                id
            ));
        }
        if !item
            .get("disallowedTools")
            .map(Value::is_array)
            .unwrap_or(false)
        {
            report.error(format!(
                "eval case `{}` disallowedTools must be an array",
                id
            ));
        }
    }
    Ok(report)
}

pub fn lint_prompts(workspace: &Path) -> Result<CheckReport> {
    let prompts_root = workspace.join("src/main/resources/prompts/rpg");
    let mut report = CheckReport::ok("prompts", 0);
    if !prompts_root.exists() {
        report.error(format!("missing directory {}", display(&prompts_root)));
        return Ok(report);
    }

    for entry in WalkDir::new(&prompts_root)
        .max_depth(1)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .filter(|entry| entry.path().extension().and_then(|ext| ext.to_str()) == Some("st"))
    {
        report.checked += 1;
        let path = entry.path();
        let content =
            fs::read_to_string(path).with_context(|| format!("reading {}", display(path)))?;
        if content.trim().is_empty() {
            report.error(format!("{} is empty", display(path)));
        }
        validate_template_placeholders(&mut report, path, &content);
    }

    if report.checked == 0 {
        report.warn("no RPG prompt templates found");
    }
    Ok(report)
}

pub fn lint_rag_docs(workspace: &Path) -> Result<CheckReport> {
    let docs_root = workspace.join("src/main/resources/document");
    let mut report = CheckReport::ok("rag docs", 0);
    if !docs_root.exists() {
        report.error(format!("missing directory {}", display(&docs_root)));
        return Ok(report);
    }

    for entry in WalkDir::new(&docs_root)
        .max_depth(1)
        .into_iter()
        .filter_entry(|entry| !is_skipped_path(entry.path()))
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .filter(|entry| entry.path().extension().and_then(|ext| ext.to_str()) == Some("md"))
    {
        report.checked += 1;
        let path = entry.path();
        let content =
            fs::read_to_string(path).with_context(|| format!("reading {}", display(path)))?;
        let Some(front_matter) = front_matter(&content) else {
            report.error(format!("{} missing YAML front matter", display(path)));
            continue;
        };
        let fields = parse_front_matter(front_matter);
        for key in ["id", "title", "tags", "updated", "source_type"] {
            if fields
                .get(key)
                .map(|value| value.trim().is_empty())
                .unwrap_or(true)
            {
                report.error(format!(
                    "{} missing required field `{}`",
                    display(path),
                    key
                ));
            }
        }

        let stem = path
            .file_stem()
            .and_then(|name| name.to_str())
            .unwrap_or("");
        if let Some(id) = fields.get("id") {
            if id.trim() != stem {
                report.error(format!(
                    "{} document id `{}` does not match filename `{}`",
                    display(path),
                    id.trim(),
                    stem
                ));
            }
        }
        if let Some(tags) = fields.get("tags") {
            let tag_count = split_tags(tags).len();
            if tag_count == 0 {
                report.error(format!("{} tags must not be empty", display(path)));
            }
        }
        if let Some(source_type) = fields.get("source_type") {
            if !matches!(source_type.trim(), "curated-demo" | "local-note") {
                report.error(format!(
                    "{} source_type must be curated-demo or local-note",
                    display(path)
                ));
            }
        }
        let body = markdown_body(&content);
        if body.chars().filter(|ch| !ch.is_whitespace()).count() < 180 {
            report.error(format!("{} document body is too short", display(path)));
        }
    }

    if report.checked == 0 {
        report.error("no Markdown files found under src/main/resources/document/*.md");
    }
    Ok(report)
}

pub fn lint_naming(workspace: &Path) -> Result<CheckReport> {
    let mut report = CheckReport::ok("naming", 0);
    let blocked_terms = [
        "寰宇智导",
        "TravelMaster",
        "TravelController",
        "TravelPromptConstant",
    ];
    let roots = [
        workspace.join("README.md"),
        workspace.join("docs"),
        workspace.join("frontend/src"),
        workspace.join("src/main/java"),
        workspace.join("src/main/resources"),
        workspace.join("src/test"),
    ];

    for root in roots {
        if !root.exists() {
            continue;
        }
        if root.is_file() {
            check_naming_file(&mut report, workspace, &root, &blocked_terms)?;
            continue;
        }
        for entry in WalkDir::new(&root)
            .into_iter()
            .filter_entry(|entry| !is_skipped_path(entry.path()))
            .filter_map(Result::ok)
            .filter(|entry| entry.file_type().is_file())
        {
            check_naming_file(&mut report, workspace, entry.path(), &blocked_terms)?;
        }
    }

    check_doc_file_names(&mut report, workspace);
    check_vue_page_names(&mut report, workspace);
    check_public_project_names(&mut report, workspace)?;
    Ok(report)
}

fn check_naming_file(
    report: &mut CheckReport,
    workspace: &Path,
    path: &Path,
    blocked_terms: &[&str],
) -> Result<()> {
    let Some(extension) = path.extension().and_then(|ext| ext.to_str()) else {
        return Ok(());
    };
    if !matches!(
        extension,
        "java" | "js" | "vue" | "json" | "md" | "yml" | "yaml" | "st"
    ) {
        return Ok(());
    }

    report.checked += 1;
    let content = fs::read_to_string(path).with_context(|| format!("reading {}", display(path)))?;
    let legacy_terms_allowed = is_governance_doc(workspace, path);
    if !legacy_terms_allowed {
        for term in blocked_terms {
            for (line_number, line) in content.lines().enumerate() {
                if contains_deprecated_term(line, term) {
                    report.error(format!(
                        "{}:{} contains deprecated naming `{}`",
                        display(path),
                        line_number + 1,
                        term
                    ));
                }
            }
        }
    }

    if extension == "json" {
        match serde_json::from_str::<Value>(&content) {
            Ok(value) => check_json_ids(report, path, &value),
            Err(error) => report.error(format!("{} JSON parse failed: {}", display(path), error)),
        }
    }
    Ok(())
}

fn check_doc_file_names(report: &mut CheckReport, workspace: &Path) {
    let docs_root = workspace.join("docs");
    if !docs_root.exists() {
        return;
    }
    for entry in WalkDir::new(&docs_root)
        .max_depth(1)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
    {
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) != Some("md") {
            continue;
        }
        let name = path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("");
        if is_release_notes_version_file(name) {
            continue;
        }
        if !is_upper_kebab_markdown(name) {
            report.warn(format!("{} should use UPPER-KEBAB-CASE.md", display(path)));
        }
    }
}

fn check_vue_page_names(report: &mut CheckReport, workspace: &Path) {
    let views_root = workspace.join("frontend/src/views");
    if !views_root.exists() {
        return;
    }
    let allowed = BTreeSet::from(["RpgHome.vue", "TravelChat.vue", "ManusChat.vue"]);
    for entry in WalkDir::new(&views_root)
        .max_depth(1)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
    {
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) != Some("vue") {
            continue;
        }
        let name = path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("");
        if allowed.contains(name) {
            continue;
        }
        if !name.ends_with("Page.vue") {
            report.warn(format!(
                "{} should be named *Page.vue or documented as an exception",
                display(path)
            ));
        }
    }
}

fn check_public_project_names(report: &mut CheckReport, workspace: &Path) -> Result<()> {
    let files = [
        (
            "pom.xml",
            vec![
                "<artifactId>wayfinder-guild</artifactId>",
                "<name>Wayfinder Guild</name>",
            ],
            vec![
                "<artifactId>sy-ai-agent</artifactId>",
                "<name>sy-ai-agent</name>",
            ],
        ),
        (
            "src/main/resources/application.yml",
            vec!["name: wayfinder-guild"],
            vec!["name: sy-ai-agent"],
        ),
        (
            "frontend/package.json",
            vec!["\"name\": \"wayfinder-guild-frontend\""],
            vec!["\"name\": \"syai-frontend\""],
        ),
        (
            "frontend/package-lock.json",
            vec!["\"name\": \"wayfinder-guild-frontend\""],
            vec!["\"name\": \"syai-frontend\""],
        ),
        (
            "frontend/index.html",
            vec!["<title>Wayfinder Guild</title>"],
            vec!["SY AI Frontend"],
        ),
        (
            "src/main/resources/rpg/projects.json",
            vec![
                "\"id\": \"wayfinder-guild\"",
                "\"name\": \"Wayfinder Guild\"",
            ],
            vec!["\"id\": \"sy-ai-agent\"", "\"name\": \"sy-ai-agent\""],
        ),
    ];

    for (relative, required_terms, forbidden_terms) in files {
        let path = workspace.join(relative);
        if !path.exists() {
            continue;
        }
        let content =
            fs::read_to_string(&path).with_context(|| format!("reading {}", display(&path)))?;
        for term in required_terms {
            if !content.contains(term) {
                report.error(format!(
                    "{} missing required public naming `{}`",
                    display(&path),
                    term
                ));
            }
        }
        for term in forbidden_terms {
            if content.contains(term) {
                report.error(format!(
                    "{} contains legacy public naming `{}`",
                    display(&path),
                    term
                ));
            }
        }
    }

    Ok(())
}

fn check_json_ids(report: &mut CheckReport, path: &Path, value: &Value) {
    check_json_ids_at(report, path, value, "$");
}

fn check_json_ids_at(report: &mut CheckReport, path: &Path, value: &Value, location: &str) {
    match value {
        Value::Object(map) => {
            if let Some(id) = map.get("id").and_then(Value::as_str) {
                if !is_kebab_case(id) {
                    report.warn(format!(
                        "{} {} id `{}` should be kebab-case",
                        display(path),
                        location,
                        id
                    ));
                }
            }
            for (key, child) in map {
                check_json_ids_at(report, path, child, &format!("{}.{}", location, key));
            }
        }
        Value::Array(items) => {
            for (index, child) in items.iter().enumerate() {
                check_json_ids_at(report, path, child, &format!("{}[{}]", location, index));
            }
        }
        _ => {}
    }
}

pub fn collect_summary(workspace: &Path) -> Result<Summary> {
    let world =
        read_json(&workspace.join("src/main/resources/rpg/world.json")).unwrap_or(Value::Null);
    let projects =
        read_json(&workspace.join("src/main/resources/rpg/projects.json")).unwrap_or(Value::Null);
    let skills =
        read_json(&workspace.join("src/main/resources/rpg/skills.json")).unwrap_or(Value::Null);
    let modules =
        read_json(&workspace.join("src/main/resources/rpg/modules.json")).unwrap_or(Value::Null);
    let evals = read_json(&workspace.join("evals/travel-cases.json")).unwrap_or(Value::Null);

    let skills_count = WalkDir::new(workspace.join("src/main/resources/skills"))
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file() && entry.file_name() == "SKILL.md")
        .filter(|entry| {
            entry.path().parent() != Some(workspace.join("src/main/resources/skills").as_path())
        })
        .count();

    let prompt_count = WalkDir::new(workspace.join("src/main/resources/prompts/rpg"))
        .max_depth(1)
        .into_iter()
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .filter(|entry| entry.path().extension().and_then(|ext| ext.to_str()) == Some("st"))
        .count();

    let rag_doc_count = WalkDir::new(workspace.join("src/main/resources/document"))
        .max_depth(1)
        .into_iter()
        .filter_entry(|entry| !is_skipped_path(entry.path()))
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_file())
        .filter(|entry| entry.path().extension().and_then(|ext| ext.to_str()) == Some("md"))
        .count();

    Ok(Summary {
        skills: skills_count,
        rpg_areas: array_len(world.get("areas")),
        rpg_npcs: array_len(world.get("npcs")),
        rpg_projects: array_len(Some(&projects)),
        rpg_skills: array_len(Some(&skills)),
        rpg_modules: array_len(Some(&modules)),
        eval_cases: array_len(Some(&evals)),
        prompt_templates: prompt_count,
        rag_docs: rag_doc_count,
    })
}

fn print_reports(reports: &[CheckReport]) {
    println!("Wayfinder CLI checks");
    println!("=====================");
    for report in reports {
        let status = if report.is_ok() { "OK" } else { "FAIL" };
        println!(
            "[{}] {}: checked {}, {} warning(s), {} error(s)",
            status,
            report.name,
            report.checked,
            report.warnings.len(),
            report.errors.len()
        );
        for warning in &report.warnings {
            println!("  warning: {}", warning);
        }
        for error in &report.errors {
            println!("  error: {}", error);
        }
    }
}

fn print_summary(summary: &Summary) {
    println!();
    println!("Wayfinder resource summary");
    println!("==========================");
    println!("skills: {}", summary.skills);
    println!("rpg areas: {}", summary.rpg_areas);
    println!("rpg npcs: {}", summary.rpg_npcs);
    println!("rpg projects: {}", summary.rpg_projects);
    println!("rpg skills: {}", summary.rpg_skills);
    println!("rpg modules: {}", summary.rpg_modules);
    println!("eval cases: {}", summary.eval_cases);
    println!("prompt templates: {}", summary.prompt_templates);
    println!("rag docs: {}", summary.rag_docs);
}

fn fail_on_errors(reports: &[CheckReport]) -> Result<()> {
    let error_count: usize = reports.iter().map(|report| report.errors.len()).sum();
    if error_count == 0 {
        Ok(())
    } else {
        anyhow::bail!("Wayfinder checks failed with {} error(s)", error_count)
    }
}

fn read_json(path: &Path) -> Result<Value> {
    let content = fs::read_to_string(path).with_context(|| format!("reading {}", display(path)))?;
    serde_json::from_str(&content).with_context(|| format!("parsing {}", display(path)))
}

fn front_matter(content: &str) -> Option<&str> {
    let rest = content.strip_prefix("---")?;
    let rest = rest
        .strip_prefix("\r\n")
        .or_else(|| rest.strip_prefix('\n'))?;
    rest.split_once("\n---")
        .map(|(front_matter, _)| front_matter)
        .or_else(|| {
            rest.split_once("\r\n---")
                .map(|(front_matter, _)| front_matter)
        })
}

fn parse_front_matter(front_matter: &str) -> BTreeMap<String, String> {
    let mut fields = BTreeMap::new();
    for line in front_matter.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once(':') {
            fields.insert(
                key.trim().to_string(),
                value.trim().trim_matches('"').to_string(),
            );
        }
    }
    fields
}

fn markdown_body(content: &str) -> &str {
    let Some(rest) = content.strip_prefix("---") else {
        return content;
    };
    let Some(rest) = rest
        .strip_prefix("\r\n")
        .or_else(|| rest.strip_prefix('\n'))
    else {
        return content;
    };
    rest.split_once("\n---")
        .map(|(_, body)| body)
        .or_else(|| rest.split_once("\r\n---").map(|(_, body)| body))
        .unwrap_or(content)
}

fn split_tags(value: &str) -> Vec<String> {
    value
        .trim()
        .trim_start_matches('[')
        .trim_end_matches(']')
        .split(',')
        .map(|tag| tag.trim().trim_matches('"').to_string())
        .filter(|tag| !tag.is_empty())
        .collect()
}

fn require_string(report: &mut CheckReport, file: &str, value: &Value, field: &str) {
    if string_field(value, field).unwrap_or("").trim().is_empty() {
        report.error(format!("{} missing non-empty `{}`", file, field));
    }
}

fn require_non_empty_array(report: &mut CheckReport, file: &str, value: &Value, field: &str) {
    match value.get(field).and_then(Value::as_array) {
        Some(items) if !items.is_empty() => {}
        _ => report.error(format!("{} missing non-empty array `{}`", file, field)),
    }
}

fn require_array_root(report: &mut CheckReport, file: &str, value: &Value) {
    match value.as_array() {
        Some(items) if !items.is_empty() => {}
        Some(_) => report.error(format!("{} must not be an empty array", file)),
        None => report.error(format!("{} must be a JSON array", file)),
    }
}

fn string_field<'a>(value: &'a Value, field: &str) -> Option<&'a str> {
    value.get(field).and_then(Value::as_str)
}

fn string_array(value: &Value, field: &str) -> Vec<String> {
    value
        .get(field)
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(Value::as_str)
        .map(str::to_owned)
        .collect()
}

fn array_len(value: Option<&Value>) -> usize {
    value.and_then(Value::as_array).map(Vec::len).unwrap_or(0)
}

fn validate_template_placeholders(report: &mut CheckReport, path: &Path, content: &str) {
    for (index, line) in content.lines().enumerate() {
        let open = line.matches('<').count();
        let close = line.matches('>').count();
        if open != close {
            report.error(format!(
                "{}:{} has unbalanced angle placeholders",
                display(path),
                index + 1
            ));
        }
        if line.contains("<<") || line.contains(">>") {
            report.warn(format!(
                "{}:{} contains repeated placeholder angle brackets",
                display(path),
                index + 1
            ));
        }
    }
}

fn is_skipped_path(path: &Path) -> bool {
    path.components().any(|component| {
        let name = component.as_os_str().to_string_lossy();
        matches!(
            name.as_ref(),
            ".git" | "node_modules" | "target" | "dist" | "private-docs"
        )
    })
}

fn is_governance_doc(workspace: &Path, path: &Path) -> bool {
    let Ok(relative) = path.strip_prefix(workspace) else {
        return false;
    };
    matches!(
        relative.to_string_lossy().replace('\\', "/").as_str(),
        "docs/NAMING-AUDIT.md" | "docs/NAMING-GUIDE.md"
    )
}

fn is_kebab_case(value: &str) -> bool {
    if value.is_empty() {
        return false;
    }
    value
        .chars()
        .all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || ch == '-')
        && !value.starts_with('-')
        && !value.ends_with('-')
        && !value.contains("--")
}

fn is_upper_kebab_markdown(name: &str) -> bool {
    let Some(stem) = name.strip_suffix(".md") else {
        return false;
    };
    if stem.is_empty() {
        return false;
    }
    stem.chars()
        .all(|ch| ch.is_ascii_uppercase() || ch.is_ascii_digit() || ch == '-')
}

fn is_release_notes_version_file(name: &str) -> bool {
    let Some(version) = name
        .strip_prefix("RELEASE-NOTES-v")
        .and_then(|value| value.strip_suffix(".md"))
    else {
        return false;
    };
    let mut parts = version.split('.');
    matches!(
        (parts.next(), parts.next(), parts.next(), parts.next()),
        (Some(major), Some(minor), Some(patch), None)
            if is_ascii_digits(major) && is_ascii_digits(minor) && is_ascii_digits(patch)
    )
}

fn is_ascii_digits(value: &str) -> bool {
    !value.is_empty() && value.chars().all(|ch| ch.is_ascii_digit())
}

fn contains_deprecated_term(line: &str, term: &str) -> bool {
    if term == "寰宇智导" {
        return line.contains(term);
    }

    let mut search_start = 0;
    while let Some(index) = line[search_start..].find(term) {
        let start = search_start + index;
        let end = start + term.len();
        let before = line[..start].chars().next_back();
        let after = line[end..].chars().next();
        if !is_identifier_char(before) && !is_identifier_char(after) {
            return true;
        }
        search_start = end;
    }
    false
}

fn is_identifier_char(ch: Option<char>) -> bool {
    ch.map(|ch| ch.is_ascii_alphanumeric() || ch == '_')
        .unwrap_or(false)
}

fn display(path: &Path) -> String {
    path.display().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::{create_dir_all, write};
    use tempfile::tempdir;

    #[test]
    fn lint_skills_accepts_valid_skill() -> Result<()> {
        let dir = tempdir()?;
        let skill_dir = dir.path().join("src/main/resources/skills/japan-travel");
        create_dir_all(&skill_dir)?;
        write(
            skill_dir.join("SKILL.md"),
            r#"---
id: japan-travel
name: Japan Travel
description: Japan-specific planning.
tags: japan, travel
triggers: japan, tokyo
priority: 90
---

## Guidance
"#,
        )?;

        let report = lint_skills(dir.path())?;
        assert!(report.is_ok(), "{:?}", report.errors);
        assert_eq!(report.checked, 1);
        Ok(())
    }

    #[test]
    fn lint_skills_rejects_id_directory_mismatch() -> Result<()> {
        let dir = tempdir()?;
        let skill_dir = dir.path().join("src/main/resources/skills/japan-travel");
        create_dir_all(&skill_dir)?;
        write(
            skill_dir.join("SKILL.md"),
            r#"---
id: kyoto-travel
name: Japan Travel
description: Japan-specific planning.
tags: japan
triggers: kyoto
priority: 90
---
"#,
        )?;

        let report = lint_skills(dir.path())?;
        assert!(!report.is_ok());
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("does not match directory")));
        Ok(())
    }

    #[test]
    fn lint_evals_validates_array_fields() -> Result<()> {
        let dir = tempdir()?;
        let eval_dir = dir.path().join("evals");
        create_dir_all(&eval_dir)?;
        write(
            eval_dir.join("travel-cases.json"),
            r#"[{
  "id": "case-1",
  "name": "Case",
  "input": "Plan a trip",
  "expectedSkills": ["japan-travel"],
  "disallowedTools": ["terminal"]
}]"#,
        )?;

        let report = lint_evals(dir.path())?;
        assert!(report.is_ok(), "{:?}", report.errors);
        assert_eq!(report.checked, 1);
        Ok(())
    }

    #[test]
    fn lint_prompts_detects_unbalanced_placeholders() -> Result<()> {
        let dir = tempdir()?;
        let prompt_dir = dir.path().join("src/main/resources/prompts/rpg");
        create_dir_all(&prompt_dir)?;
        write(prompt_dir.join("npc-persona.st"), "Hello <name\n")?;

        let report = lint_prompts(dir.path())?;
        assert!(!report.is_ok());
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("unbalanced")));
        Ok(())
    }

    #[test]
    fn lint_rag_docs_accepts_valid_curated_markdown() -> Result<()> {
        let dir = tempdir()?;
        let docs_dir = dir.path().join("src/main/resources/document");
        create_dir_all(&docs_dir)?;
        write(
            docs_dir.join("rainy-day-backup-plan.md"),
            format!(
                r#"---
id: rainy-day-backup-plan
title: Rainy Day Backup Plan
tags: rain, backup
updated: 2026-05-10
source_type: curated-demo
---

## Guidance

{}
"#,
                "A useful travel knowledge body with planning guidance. ".repeat(12)
            ),
        )?;

        let report = lint_rag_docs(dir.path())?;
        assert!(report.is_ok(), "{:?}", report.errors);
        assert_eq!(report.checked, 1);
        Ok(())
    }

    #[test]
    fn lint_rag_docs_rejects_missing_metadata() -> Result<()> {
        let dir = tempdir()?;
        let docs_dir = dir.path().join("src/main/resources/document");
        create_dir_all(&docs_dir)?;
        write(
            docs_dir.join("bad-doc.md"),
            r#"---
id: other-id
title: Bad Doc
tags:
updated: 2026-05-10
source_type: remote
---

Short.
"#,
        )?;

        let report = lint_rag_docs(dir.path())?;
        assert!(!report.is_ok());
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("does not match filename")));
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("source_type")));
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("too short")));
        Ok(())
    }

    #[test]
    fn lint_naming_rejects_legacy_branding() -> Result<()> {
        let dir = tempdir()?;
        let docs_dir = dir.path().join("docs");
        create_dir_all(&docs_dir)?;
        write(docs_dir.join("README.md"), "寰宇智导\n")?;

        let report = lint_naming(dir.path())?;
        assert!(!report.is_ok());
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("deprecated naming")));
        Ok(())
    }

    #[test]
    fn lint_naming_allows_governance_docs_to_mention_old_names() -> Result<()> {
        let dir = tempdir()?;
        let docs_dir = dir.path().join("docs");
        create_dir_all(&docs_dir)?;
        write(docs_dir.join("NAMING-AUDIT.md"), "TravelMaster\n")?;

        let report = lint_naming(dir.path())?;
        assert!(report.is_ok(), "{:?}", report.errors);
        Ok(())
    }

    #[test]
    fn lint_naming_warns_about_json_ids_and_vue_pages() -> Result<()> {
        let dir = tempdir()?;
        let resource_dir = dir.path().join("src/main/resources/rpg");
        let views_dir = dir.path().join("frontend/src/views");
        create_dir_all(&resource_dir)?;
        create_dir_all(&views_dir)?;
        write(resource_dir.join("world.json"), r#"{ "id": "Bad_Id" }"#)?;
        write(views_dir.join("Home.vue"), "<template></template>")?;

        let report = lint_naming(dir.path())?;
        assert!(report.is_ok(), "{:?}", report.errors);
        assert!(report
            .warnings
            .iter()
            .any(|warning| warning.contains("kebab-case")));
        assert!(report
            .warnings
            .iter()
            .any(|warning| warning.contains("*Page.vue")));
        Ok(())
    }

    #[test]
    fn lint_naming_rejects_legacy_public_project_contracts() -> Result<()> {
        let dir = tempdir()?;
        let frontend_dir = dir.path().join("frontend");
        create_dir_all(&frontend_dir)?;
        write(
            frontend_dir.join("package.json"),
            r#"{ "name": "syai-frontend" }"#,
        )?;

        let report = lint_naming(dir.path())?;
        assert!(!report.is_ok());
        assert!(report
            .errors
            .iter()
            .any(|error| error.contains("legacy public naming")));
        Ok(())
    }
}
