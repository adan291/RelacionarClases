# Benchmark del análisis de relaciones

Medido con JMH 1.37 (perfil `bench`, código en `src/jmh/java`). Los JSON de cada pasada están en `docs/bench/`.

## Entorno

- CPU: AMD Ryzen 7 9800X3D (8 núcleos, 16 hilos), 31,7 GB de RAM, Windows 11 Home.
- JDK 25: Temurin 25.0.4.1+1-LTS.
- JDK 28: OpenJDK 28-ea+18-1391 (early access con Valhalla).
- La máquina no estaba aislada: había un IDE, otros servicios Java y un navegador abiertos. Las diferencias por debajo del 10 % hay que tomarlas como ruido.

## Qué se mide

- **Análisis completo** (`FullAnalysisBenchmark`): `IntelligentRelationAnalyzer.analizar(Map)` sobre un modelo sintético en memoria (`SyntheticModel`, sin disco) de 50, 200 y 500 clases, con 20 decisiones en memoria y ML activo. Incluye estructural, semántico, ML, combinación, memoria y detección bidireccional.
- **Ajuste por memoria** (`MemoryAdjustBenchmark`): 500 relaciones contra 1.000 decisiones.
- **Extracción de features** (`HotPathsBenchmark.featureExtraction`): 1.000 pares × 4 tipos = 4.000 vectores.
- **Similitud de nombres** (`HotPathsBenchmark.nameSimilarity`): 1.000 llamadas a `SemanticSimilarity.compare`.

Modo `AverageTime`, 1 fork. Las pasadas 01 y 02 usaron 2 iteraciones de calentamiento y 3 de medida en los benchmarks de análisis y memoria; las 03 a 05, 3 y 5 (`-wi 3 -i 5`). El error es el intervalo del 99,9 % que da JMH.

## Resultados

| Benchmark | 01 línea base (JDK 25) | 02 algoritmo optimizado (JDK 25) | 03 final (JDK 25) | 04 final, JDK 28 sin preview | 05 final, JDK 28 + Valhalla |
|---|---|---|---|---|---|
| Análisis, 50 clases (ms) | 168,8 ± 10,5 | 7,7 ± 6,6 | 7,0 ± 0,5 | 7,4 ± 0,9 | 8,0 ± 0,8 |
| Análisis, 200 clases (ms) | 3.673,6 ± 159,6 | 161,1 ± 59,1 | 148,5 ± 37,9 | 151,8 ± 15,6 | 154,4 ± 7,1 |
| Análisis, 500 clases (ms) | 22.986,2 ± 2.188,7 | 1.090,2 ± 2.839,8 | 1.051,5 ± 23,5 | 999,2 ± 84,1 | 949,3 ± 174,5 |
| Ajuste por memoria, 1.000 decisiones (ms) | 1.854,3 ± 37,7 | 198,0 ± 173,5 | 146,8 ± 10,2 | 172,8 ± 16,2 | 154,5 ± 59,3 |
| Extracción de features, 4.000 vectores (µs) | 8.514 ± 116 | 7.476 ± 1.062 | 6.818 ± 241 | 7.249 ± 220 | 7.522 ± 594 |
| Similitud de nombres, 1.000 pares (µs) | 1.719 ± 77 | 845 ± 67 | 931 ± 48 | 824 ± 26 | 861 ± 68 |

- **01**: commit `4b0e3df`, antes de optimizar.
- **02**: commit `f1ef8e6`, optimización algorítmica con los mismos resultados (lo comprueba `AnalysisEquivalenceTest`).
- **03 a 05**: el mismo código final (incluye la corrección de `contains` y de la feature 2, que cambia algo las confianzas, y la API). 04 y 05 solo se diferencian en que 05 compila con `--release 28 --enable-preview` y `FieldInfo`, `RelationKey`, `PairScore` y `ScoreInfo` son `value record`.
- Una pasada repetida por error de 04 (JDK 28 sin preview, no se guardó el JSON) dio 7,3 / 144,6 / 976,4 ms en el análisis de 50 / 200 / 500 clases y 166,6 ms en memoria, dentro del margen de 04.

## Conclusiones

1. **El coste estaba en el algoritmo.** El análisis de 500 clases baja de 23,0 s a 1,05 s (unas 22 veces) y el de 200 de 3,7 s a 0,15 s (unas 25 veces). El ajuste por memoria con 1.000 decisiones baja de 1,85 s a 0,15 s (unas 12 veces). Las causas eran la similitud calculada 4 veces por par, el vector de features calculado 4 veces por par, el `split` con expresión regular por cada par relación × decisión y el `String.format` por relación al combinar.
2. **Valhalla no aporta una mejora medible.** Entre 03, 04 y 05 las diferencias (entre −11 % y +14 %) son del tamaño del error de medida, y cambian de signo según el benchmark. Con 500 clases, 05 sale un 5 % por debajo de 04, pero su error es de ±18 %; con 50 clases sale un 9 % por encima. No hay una ganancia que se pueda atribuir a los value records.
3. **Por qué no aporta:** el tiempo se va en operaciones con cadenas (Jaro-Winkler de commons-text, Levenshtein, `toLowerCase`, búsquedas en `HashMap` con claves `String`) y en el producto escalar del modelo, que ya trabaja con `double[]`. Los value records del proyecto viven sobre todo dentro de colecciones genéricas (`HashMap<RelationKey, ScoreInfo>`, `List<FieldInfo>`), donde JEP 401 no los aplana, y `PairScore` ya era un objeto de vida corta que el JIT podía eliminar con escape analysis.
4. **Recomendación:** mantener Java 25 como base y el perfil `valhalla` como experimento, sin hacerlo el predeterminado. Si se quiere sacar partido de Valhalla habría que rediseñar las estructuras calientes con arrays de value objects planos, y eso solo merece la pena si un perfilado lo justifica.

## Cómo repetirlo

```powershell
# JDK 25
mvn -B -Pbench clean test-compile exec:exec "-Dbench.args=-wi 3 -i 5 -rf json -rff target/jmh.json"

# JDK 28 + Valhalla
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jdk-28"
mvn -B "-Pvalhalla,bench" clean test-compile exec:exec "-Dbench.args=-wi 3 -i 5 -rf json -rff target/jmh.json"
```

Para un solo benchmark: `-Dbench.args="FullAnalysis -p classes=500"`.
