# Requirements: Diagram Export (PlantUML/Mermaid)

## Overview
Generate entity-relationship diagrams from detected relations in PlantUML and Mermaid formats for documentation and visualization.

## Actors
- User: Exports diagrams for documentation
- Application: Generates diagram code
- External Tools: PlantUML server, Mermaid renderers, GitHub/GitLab

## Functional Requirements

### 1. PlantUML Export
- 1.1 Generate valid PlantUML class diagram syntax
- 1.2 Include all detected entities as classes
- 1.3 Show relationships with correct cardinality notation
- 1.4 Include field names on relationship lines
- 1.5 Support bidirectional relationship notation
- 1.6 Add title and metadata comments

### 2. Mermaid Export
- 2.1 Generate valid Mermaid ER diagram syntax
- 2.2 Include all entities with their key fields
- 2.3 Show relationships with cardinality (||, |{, }|, }o, o{)
- 2.4 Include relationship labels
- 2.5 Support GitHub/GitLab markdown embedding

### 3. Export Options
- 3.1 Export all relations or only accepted relations
- 3.2 Filter by confidence threshold
- 3.3 Filter by relation type
- 3.4 Include/exclude pending relations
- 3.5 Option to show confidence scores as comments

### 4. File Output
- 4.1 Save to .puml file for PlantUML
- 4.2 Save to .md file with Mermaid code block
- 4.3 Copy to clipboard option
- 4.4 Auto-generate filename based on project name

### 5. UI Integration
- 5.1 Add "📄 Exportar Diagrama" button to toolbar
- 5.2 Show export dialog with format selection
- 5.3 Preview generated code before saving
- 5.4 Show success message with file path

### 6. Diagram Styling
- 6.1 Color-code entities by package/module
- 6.2 Highlight bidirectional relationships
- 6.3 Show relationship direction with arrows
- 6.4 Group related entities visually

## Non-Functional Requirements
- Compatibility: Generated code should render correctly in standard tools
- Readability: Output should be human-readable and editable
- Performance: Export should complete in <1 second for 100 relations
