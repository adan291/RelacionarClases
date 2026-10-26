# Dynamic Backend Generator - Convenciones del Proyecto

## Visión del Proyecto
Generador modular de backends Spring Boot que guía al usuario paso a paso para crear aplicaciones completas con entidades, DTOs, mappers, servicios, controllers, tests y Docker.

## Stack Tecnológico

### Lenguaje y Framework
- Java 17+ (LTS)
- Spring Boot 3.2+
- Maven como gestor de dependencias
- JUnit 5 + jqwik para property-based testing

### Dependencias Core del Generador
- JavaParser para análisis y generación de código
- Mustache/FreeMarker para templates
- Spring Shell para wizard interactivo CLI

## Estructura de Módulos

```
backend-generator/
├── generator-core/           # Lógica común, utilities, templates base
├── generator-entity/         # Generación de @Entity con relaciones JPA
├── generator-dto-mapper/     # DTOs y MapStruct mappers
├── generator-repository/     # JpaRepository interfaces
├── generator-service/        # Services con CRUD
├── generator-controller/     # REST Controllers
├── generator-test/           # Tests unitarios e integración
├── generator-docker/         # Dockerfile, docker-compose
├── generator-structure/      # Arquitecturas (MVC, Hexagonal, Clean)
└── generator-cli/            # Wizard interactivo (orquestador)
```

## Convenciones de Código

### Naming
- Clases: PascalCase (`EntityGenerator`, `DtoTemplateEngine`)
- Métodos: camelCase (`generateEntity`, `parseRelations`)
- Constantes: UPPER_SNAKE_CASE (`DEFAULT_PACKAGE_NAME`)
- Paquetes: lowercase (`org.example.generator.entity`)

### Estructura de Paquetes por Módulo
```
org.example.generator.<modulo>/
├── api/          # Interfaces públicas del módulo
├── impl/         # Implementaciones
├── model/        # POJOs, DTOs internos
├── template/     # Templates de generación
└── util/         # Utilidades específicas
```

### Patrones de Diseño a Usar
- **Builder** → Para configuración de generadores
- **Strategy** → Para diferentes arquitecturas (MVC, Hexa)
- **Template Method** → Para flujo de generación común
- **Factory** → Para crear generadores según contexto
- **Chain of Responsibility** → Para pipeline de transformaciones

## Convenciones de Generación

### Código Generado
El código que genera la herramienta debe seguir:
- Anotaciones JPA estándar (@Entity, @Table, @Column)
- Lombok para reducir boilerplate (@Data, @Builder, @NoArgsConstructor)
- Validaciones con Jakarta Validation (@NotNull, @Size)
- OpenAPI annotations para documentación automática

### Arquitecturas Soportadas
1. **MVC Clásico**: Controller → Service → Repository
2. **Hexagonal**: Adapters → Ports → Domain
3. **Clean Architecture**: Controllers → UseCases → Entities

## Flujo del Wizard Interactivo

```
1. Bienvenida + selección de arquitectura
2. Definición de entidades (nombre, campos, tipos)
3. Detección/confirmación de relaciones
4. Configuración de API REST
5. Selección de features (DTOs, Mappers, Tests, Docker)
6. Generación + resumen
```

## Testing

### Tests del Generador
- Unit tests para cada componente
- Property-based tests para validar invariantes
- Integration tests para flujos completos

### Tests Generados
- El código generado incluirá tests básicos
- Tests de repository con @DataJpaTest
- Tests de service con mocks
- Tests de controller con MockMvc

## Documentación
- Javadoc en clases públicas
- README.md por módulo
- Ejemplos de uso en cada generador
