# Implementation Plan

- [ ] 1. Create i18n infrastructure

  - [ ] 1.1 Create I18n singleton class
    - Implement getInstance() method
    - Implement initialize() to load default locale
    - Implement setLocale(Locale) to change language
    - Implement get(String key) for simple strings
    - Implement get(String key, Object... args) for parameterized strings
    - _Requirements: 1.4, 4.1_

  - [ ] 1.2 Create LocaleChangeListener interface
    - Define onLocaleChanged(Locale) method
    - Add listener management to I18n class
    - _Requirements: 4.4_

  - [ ] 1.3 Implement date/number formatting
    - Implement formatDate(LocalDateTime) using DateTimeFormatter
    - Implement formatNumber(double) using NumberFormat
    - Implement formatPercent(double) using NumberFormat
    - _Requirements: 5.1, 5.2, 5.3_

  - [ ] 1.4 Write property test for parameterized messages
    - **Property 2: Parameterized Messages Work**
    - Test {0}, {1} placeholder substitution
    - **Validates: Requirement 1.4**

- [ ] 2. Create resource bundles

  - [ ] 2.1 Create messages.properties (Spanish default)
    - Add all UI button labels
    - Add all table column headers
    - Add all dialog messages
    - Add all status messages
    - Add all filter labels
    - _Requirements: 1.1, 2.1-2.6_

  - [ ] 2.2 Create messages_en.properties (English)
    - Translate all keys from Spanish bundle
    - Maintain same key structure
    - _Requirements: 1.2_

  - [ ] 2.3 Write property test for key completeness
    - **Property 1: All Keys Have Values**
    - Verify English has all keys from Spanish
    - **Validates: Requirements 1.1, 1.2**

- [ ] 3. Externalize RelationAnalyzerAppView strings

  - [ ] 3.1 Externalize toolbar button labels
    - Analizar, Aceptar, Rechazar, Exportar, etc.
    - _Requirements: 2.1_

  - [ ] 3.2 Externalize table column headers
    - Origen, Destino, Tipo, Confianza, etc.
    - _Requirements: 2.4_

  - [ ] 3.3 Externalize filter labels
    - Confianza mínima, Tipo, Fuente, Buscar
    - _Requirements: 2.2_

  - [ ] 3.4 Externalize status bar messages
    - Analysis status, ML status, relation counts
    - _Requirements: 2.6_

  - [ ] 3.5 Externalize tooltips
    - Button tooltips, table cell tooltips
    - _Requirements: 2.5_

- [ ] 4. Externalize dialog strings

  - [ ] 4.1 Externalize HistoryPanelView strings
    - Dialog title, button labels, list headers
    - _Requirements: 2.3_

  - [ ] 4.2 Externalize ExportDialog strings
    - Format labels, filter labels, button labels
    - _Requirements: 2.3_

  - [ ] 4.3 Externalize confirmation dialogs
    - Bulk action confirmations
    - Delete confirmations
    - Restore confirmations
    - _Requirements: 6.2_

  - [ ] 4.4 Externalize error/success messages
    - File operation errors
    - Analysis errors
    - Success notifications
    - _Requirements: 6.1, 6.3_

- [ ] 5. Externalize BulkActionsToolbar strings

  - [ ] 5.1 Externalize button labels
    - Aceptar, Rechazar, selection counter
    - _Requirements: 2.1_

  - [ ] 5.2 Externalize menu items
    - Smart selection options
    - _Requirements: 2.2_

- [ ] 6. Externalize GraphVisualizationView strings

  - [ ] 6.1 Externalize window title and labels
    - Graph title, legend labels
    - _Requirements: 2.3_

  - [ ] 6.2 Externalize tooltips
    - Node tooltips, edge tooltips
    - _Requirements: 2.5_

- [ ] 7. Implement language selection UI

  - [ ] 7.1 Add language selector to toolbar
    - ComboBox with available languages
    - Show current language
    - _Requirements: 4.2_

  - [ ] 7.2 Implement language persistence
    - Save selected language to preferences/config
    - Load on startup
    - _Requirements: 4.3_

  - [ ] 7.3 Implement UI refresh on language change
    - Register components as LocaleChangeListeners
    - Refresh labels when locale changes
    - _Requirements: 4.4_

  - [ ] 7.4 Write property test for locale change propagation
    - **Property 3: Locale Change Propagates**
    - **Validates: Requirement 4.4**

- [ ] 8. Externalize log messages

  - [ ] 8.1 Externalize service layer log messages
    - MemoryService, AnalysisHistoryService messages
    - _Requirements: 3.1_

  - [ ] 8.2 Externalize ML layer log messages
    - Training, prediction, evaluation messages
    - _Requirements: 3.1, 3.2_

  - [ ] 8.3 Externalize analyzer log messages
    - Analysis progress, results messages
    - _Requirements: 3.1_

- [ ] 9. Write fallback test

  - [ ] 9.1 Write property test for fallback behavior
    - **Property 4: Fallback Works**
    - Missing English key returns Spanish value
    - **Validates: Non-functional fallback requirement**

- [ ] 10. Final Checkpoint - Ensure all tests pass
  - Run mvn test
  - Verify Spanish UI displays correctly
  - Verify English UI displays correctly
  - Verify language switching works
