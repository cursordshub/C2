# Milestone
M3 – LLM Prompt Automation: Generate targeted prompts per class to drive coverage-focused test-case creation, filling gaps interactively.

# Problem Statement
Milestone 3 – LLM Prompt Automation Even with scaffolding, writing effective branch/mutation-covering test cases requires contextual reasoning an LLM can assist with, but ad-hoc prompting yields inconsistent results. A prompt-generation script is needed that packages class metadata and generated infra into structured, high-quality prompts for chat-based LLMs to complete coverage gaps reliably.


# Requirements analysis
M3 – LLM Prompt Automation: Requirements
• Serialize metadata + generated infra into structured, token-efficient prompt templates.
• Target uncovered branches/mutations (needs Jacoco/PIT report ingestion).
• Support iterative gap-filling via chat.
Components/Libraries:
• Coverage data: org.jacoco.core (JaCoCo API), PIT mutation reports (XML parsing via javax.xml.parsers).
Prompt templating: same as M2 (Velocity/FreeMarker) or plain string builders.

# Implementation Plan

   ## High Level
M3 – LLM Prompt Automation Input: M1 metadata + M2 stubs + JaCoCo/PIT reports. Process: templating builds targeted prompts. Output: chat-ready prompts filling coverage gaps. Fully downstream of M1+M2.

   ## Low Level
M3 – LLM Prompt Automation (Low-Level)
    • Parse JaCoCo XML/PIT mutation reports; map uncovered lines/mutants to method-level granularity per FQN.
    • Cross-reference uncovered branches with M1 metadata (method signatures) and M2 stub (existing scaffolding).
    • Build prompt template combining: class context, dependency metadata, existing test skeleton, targeted uncovered branches/mutations.
    • Inject directive rules (from prior prompt-engineering work) for LLM output format constraints.
Output: per-class .md/.txt prompt file ready for chat-window paste. Upstream: M1+M2 outputs+coverage reports; terminal stage, no downstream dependency.

# Technical Spec
M3 – LLM Prompt Automation Spec
• Artifacts: JaCoCo org.jacoco.core:org.jacoco.report:0.8.12 (or raw XML parse via javax.xml.parsers); no new runtime deps for target.
• Folder split: promptbuilder/{CoverageMapper, PromptTemplateEngine, PromptWriter}; resources/templates/*.txt.
• Path resolution: reads JaCoCo/PIT XML from target's build/reports/; outputs prompts to solution's output/prompts/, kept separate from target repo.
Strategy: manual chat-paste workflow for MVP; fat-jar packaging (shadow plugin) deferred until design stabilizes; single local machine, no CI/deploy yet.

