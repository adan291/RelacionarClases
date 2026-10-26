# Implementation Plan

- [x] 1. Create export data models


  - [x] 1.1 Create ExportOptions record


    - Define DiagramFormat enum (PLANTUML, MERMAID)
    - Include filter options (acceptedOnly, includePending, minConfidence)
    - Include relation type filter set
    - Include styling options (showConfidence, colorByPackage)
    - Add static defaults() factory method
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_


  - [x] 1.2 Create ExportResult record

    - Include generated content string
    - Include entity count and relation count

    - Include suggested filename
    - _Requirements: 4.4_



- [x] 2. Implement PlantUML generator





  - [x] 2.1 Create PlantUMLGenerator class

    - Implement generate(List<RelacionDetectada>, ExportOptions) method
    - Generate @startuml/@enduml wrapper
    - Add title and generation comment
    - _Requirements: 1.1, 1.6_


  - [x] 2.2 Implement entity extraction


    - Extract unique class names from relations
    - Generate class declarations
    - Group by package if colorByPackage enabled
    - _Requirements: 1.2, 6.1_


  - [x] 2.3 Implement relationship generation


    - Map relation types to PlantUML cardinality ("1", "*")
    - Generate relationship lines with field names

    - Handle bidirectional relationships
    - _Requirements: 1.3, 1.4, 1.5_

  - [x] 2.4 Write property test for valid PlantUML syntax


    - **Property 1: Valid PlantUML Syntax**
    - Verify @startuml/@enduml present
    - Verify class declarations valid
    - **Validates: Requirement 1.1**


- [x] 3. Implement Mermaid generator







  - [x] 3.1 Create MermaidGenerator class


    - Implement generate(List<RelacionDetectada>, ExportOptions) method
    - Generate erDiagram header

    - Add comment with title and metadata
    - _Requirements: 2.1, 2.5_



  - [x] 3.2 Implement entity generation

    - Convert class names to UPPER_SNAKE_CASE for Mermaid
    - _Requirements: 2.2_



  - [x] 3.3 Implement relationship generation
    - Map relation types to Mermaid notation (||, |{, }|, }o, o{)


    - Generate relationship lines with labels
    - _Requirements: 2.3, 2.4_

  - [x] 3.4 Write property test for valid Mermaid syntax

    - **Property 2: Valid Mermaid Syntax**
    - Verify erDiagram header present
    - Verify relationship syntax valid

    - **Validates: Requirement 2.1**

- [x] 4. Implement DiagramExportService





  - [x] 4.1 Create DiagramExportService class


    - Inject PlantUMLGenerator and MermaidGenerator

    - Implement export(relations, options) method
    - Route to appropriate generator based on format
    - _Requirements: 1.1, 2.1_


  - [x] 4.2 Implement filter application

    - Filter by accepted/pending status
    - Filter by confidence threshold

    - Filter by relation type
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 4.3 Write property test for filter application

    - **Property 5: Filter Application**
    - **Validates: Requirements 3.1, 3.2, 3.3**




  - [x] 4.4 Implement file export

    - Save to .puml file for PlantUML
    - Save to .md file with code block for Mermaid
    - Generate filename from project name
    - _Requirements: 4.1, 4.2, 4.4_


  - [x] 4.5 Implement clipboard copy

    - Copy generated content to system clipboard
    - _Requirements: 4.3_

- [x] 5. Write additional property tests






  - [x] 5.1 Write property test for entity inclusion


    - **Property 3: All Entities Included**
    - Every class from relations appears in output
    - **Validates: Requirements 1.2, 2.2**

  - [x] 5.2 Write property test for cardinality mapping


    - **Property 4: Cardinality Correctness**
    - OneToOne -> correct notation
    - OneToMany -> correct notation
    - **Validates: Requirements 1.3, 2.3**


- [x] 6. Create ExportDialog UI






  - [x] 6.1 Create ExportDialog class

    - Extend Dialog<ExportResult>
    - Add format ComboBox (PlantUML, Mermaid)
    - Add filter checkboxes and inputs
    - _Requirements: 5.2_


  - [x] 6.2 Implement preview pane

    - TextArea with generated code
    - Update preview on option changes
    - Syntax highlighting if possible
    - _Requirements: 5.3_


  - [x] 6.3 Implement action buttons

    - "Copiar" button -> copy to clipboard
    - "Guardar..." button -> file save dialog
    - "Cancelar" button -> close dialog
    - _Requirements: 4.3, 5.4_

- [x] 7. Integrate with main application






  - [x] 7.1 Add export button to RelationAnalyzerAppView


    - Add "📄 Exportar" button to toolbar
    - Wire to open ExportDialog
    - Pass current relations list
    - _Requirements: 5.1_


  - [x] 7.2 Show success feedback

    - Display confirmation with file path after save
    - Show "Copiado al portapapeles" after copy
    - _Requirements: 5.4_


- [x] 8. Final Checkpoint - Ensure all tests pass




  - Run mvn test
  - Verify PlantUML output renders correctly
  - Verify Mermaid output renders in GitHub preview
