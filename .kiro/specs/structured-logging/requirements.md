# Requirements: Structured Logging

## Overview
Replace System.out/System.err calls with proper java.util.logging throughout the application for better log management, configurability, and production readiness.

## Actors
- Developer: Configures logging levels and reviews logs
- Application: Generates structured log messages
- Log System: Manages log output, rotation, and formatting

## Functional Requirements

### 1. Logger Infrastructure
- 1.1 Each class should have its own Logger instance using class name
- 1.2 Support standard log levels: SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST
- 1.3 Provide centralized logging configuration via properties file
- 1.4 Support console and file handlers simultaneously

### 2. Log File Management
- 2.1 Write logs to rotating files in a logs/ directory
- 2.2 Implement file rotation based on size (max 5MB per file)
- 2.3 Keep maximum of 10 rotated log files
- 2.4 Use consistent naming pattern: app_%g.log (where %g is generation number)

### 3. Log Format
- 3.1 Include timestamp in ISO-8601 format
- 3.2 Include log level
- 3.3 Include class and method name
- 3.4 Include thread name for concurrent operations
- 3.5 Support custom formatter for readable output

### 4. Migration
- 4.1 Replace all System.out.println with appropriate logger.info/fine
- 4.2 Replace all System.err.println with logger.warning/severe
- 4.3 Replace all printStackTrace() with logger.log(Level.SEVERE, msg, exception)
- 4.4 Preserve existing log messages content

### 5. Configuration
- 5.1 Load logging configuration from logging.properties file
- 5.2 Allow runtime log level changes without restart
- 5.3 Support environment-specific configurations (dev vs prod)
- 5.4 Default to INFO level for production, FINE for development

## Non-Functional Requirements
- Performance: Logging should not impact application performance significantly
- Reliability: Log messages should not be lost during normal operation
- Maintainability: Easy to add new loggers and modify configuration
