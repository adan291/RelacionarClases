# Reglas de Generación de Código

## Principios Fundamentales

### 1. Código Limpio y Mantenible
- El código generado debe ser legible como si lo hubiera escrito un humano
- Evitar código duplicado - usar herencia o composición
- Nombres descriptivos que expliquen la intención

### 2. Configuración sobre Código
- Preferir configuración externa (YAML/properties) sobre hardcoding
- Valores por defecto sensatos
- Fácil de customizar sin modificar código

### 3. Fail Fast
- Validar inputs al inicio del proceso
- Mensajes de error claros y accionables
- No generar código parcial si hay errores

## Templates de Generación

### Estructura de Template
```
templates/
├── entity/
│   ├── entity.java.mustache
│   ├── entity-lombok.java.mustache
│   └── entity-validation.java.mustache
├── dto/
│   ├── request-dto.java.mustache
│   └── response-dto.java.mustache
├── mapper/
│   └── mapstruct-mapper.java.mustache
├── repository/
│   └── jpa-repository.java.mustache
├── service/
│   ├── service-interface.java.mustache
│   └── service-impl.java.mustache
└── controller/
    └── rest-controller.java.mustache
```

### Variables de Template Estándar
```java
// Siempre disponibles en todos los templates
{{packageName}}      // org.example.demo
{{className}}        // Usuario
{{classNameLower}}   // usuario
{{tableName}}        // usuarios
{{timestamp}}        // Fecha de generación
{{generatorVersion}} // Versión del generador
```

## Reglas por Tipo de Artefacto

### Entidades (@Entity)
```java
// SIEMPRE incluir
@Entity
@Table(name = "{{tableName}}")
@Data  // Si usa Lombok
@NoArgsConstructor
@AllArgsConstructor

// Campo ID obligatorio
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// Auditoría opcional pero recomendada
@CreatedDate
private LocalDateTime createdAt;

@LastModifiedDate  
private LocalDateTime updatedAt;
```

### DTOs
```java
// Request DTO - para crear/actualizar
public record {{className}}RequestDto(
    @NotBlank String campo1,
    @NotNull Long campo2
) {}

// Response DTO - para respuestas
public record {{className}}ResponseDto(
    Long id,
    String campo1,
    LocalDateTime createdAt
) {}
```

### Mappers (MapStruct)
```java
@Mapper(componentModel = "spring")
public interface {{className}}Mapper {
    {{className}} toEntity({{className}}RequestDto dto);
    {{className}}ResponseDto toDto({{className}} entity);
    List<{{className}}ResponseDto> toDtoList(List<{{className}}> entities);
}
```

### Repositories
```java
@Repository
public interface {{className}}Repository 
    extends JpaRepository<{{className}}, Long> {
    
    // Métodos derivados según campos únicos
    Optional<{{className}}> findBy{{uniqueField}}({{fieldType}} value);
}
```

### Services
```java
public interface {{className}}Service {
    {{className}}ResponseDto create({{className}}RequestDto dto);
    {{className}}ResponseDto getById(Long id);
    Page<{{className}}ResponseDto> getAll(Pageable pageable);
    {{className}}ResponseDto update(Long id, {{className}}RequestDto dto);
    void delete(Long id);
}
```

### Controllers
```java
@RestController
@RequestMapping("/api/v1/{{pathName}}")
@RequiredArgsConstructor
@Tag(name = "{{className}}")
public class {{className}}Controller {
    
    private final {{className}}Service service;
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public {{className}}ResponseDto create(@Valid @RequestBody {{className}}RequestDto dto);
    
    @GetMapping("/{id}")
    public {{className}}ResponseDto getById(@PathVariable Long id);
    
    @GetMapping
    public Page<{{className}}ResponseDto> getAll(Pageable pageable);
    
    @PutMapping("/{id}")
    public {{className}}ResponseDto update(@PathVariable Long id, @Valid @RequestBody {{className}}RequestDto dto);
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id);
}
```

## Manejo de Relaciones JPA

### OneToMany / ManyToOne
```java
// Lado "One" (padre)
@OneToMany(mappedBy = "{{parentField}}", cascade = CascadeType.ALL)
private List<{{childClass}}> {{childrenField}} = new ArrayList<>();

// Lado "Many" (hijo)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "{{parentField}}_id")
private {{parentClass}} {{parentField}};
```

### ManyToMany
```java
@ManyToMany
@JoinTable(
    name = "{{joinTableName}}",
    joinColumns = @JoinColumn(name = "{{thisClass}}_id"),
    inverseJoinColumns = @JoinColumn(name = "{{otherClass}}_id")
)
private Set<{{otherClass}}> {{otherClassPlural}} = new HashSet<>();
```

## Validaciones Automáticas

### Campos String
- `@NotBlank` si es requerido
- `@Size(max = X)` según tipo de columna
- `@Email` si el nombre contiene "email"
- `@Pattern` para formatos específicos

### Campos Numéricos
- `@NotNull` si es requerido
- `@Min` / `@Max` según contexto
- `@Positive` para IDs y cantidades

### Campos Fecha
- `@NotNull` si es requerido
- `@PastOrPresent` para fechas de creación
- `@Future` para fechas de vencimiento
