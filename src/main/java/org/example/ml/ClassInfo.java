package org.example.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents structural information about a Java class.
 * Used by FeatureExtractor to extract features for ML model.
 */
public class ClassInfo {
    
    private String className;
    private List<String> fieldTypes;
    private boolean hasCollections;
    private List<String> collectionTypes;
    
    // Enhanced fields for ML features
    private List<FieldInfo> fields;
    private List<String> interfaces;
    private String superClass;
    private boolean hasIdField;
    private List<String> constructorParams;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public ClassInfo() {
        this.fieldTypes = new ArrayList<>();
        this.collectionTypes = new ArrayList<>();
        this.hasCollections = false;
        this.fields = new ArrayList<>();
        this.interfaces = new ArrayList<>();
        this.constructorParams = new ArrayList<>();
        this.hasIdField = false;
    }
    
    /**
     * Creates a new ClassInfo with the specified class name.
     * @param className the name of the class
     */
    public ClassInfo(String className) {
        this();
        this.className = className;
    }
    
    /**
     * Creates a fully specified ClassInfo.
     * @param className the name of the class
     * @param fieldTypes list of field type names
     * @param hasCollections whether the class has collection fields
     * @param collectionTypes types contained in collections (e.g., "Cliente" from List<Cliente>)
     */
    public ClassInfo(String className, List<String> fieldTypes, 
                     boolean hasCollections, List<String> collectionTypes) {
        this.className = className;
        this.fieldTypes = fieldTypes != null ? new ArrayList<>(fieldTypes) : new ArrayList<>();
        this.hasCollections = hasCollections;
        this.collectionTypes = collectionTypes != null ? new ArrayList<>(collectionTypes) : new ArrayList<>();
    }

    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public List<String> getFieldTypes() {
        return Collections.unmodifiableList(fieldTypes);
    }
    
    public void setFieldTypes(List<String> fieldTypes) {
        this.fieldTypes = fieldTypes != null ? new ArrayList<>(fieldTypes) : new ArrayList<>();
    }
    
    /**
     * Adds a field type to the class info.
     * @param fieldType the type of the field
     */
    public void addFieldType(String fieldType) {
        if (fieldType != null) {
            this.fieldTypes.add(fieldType);
        }
    }
    
    public boolean hasCollections() {
        return hasCollections;
    }
    
    public void setHasCollections(boolean hasCollections) {
        this.hasCollections = hasCollections;
    }
    
    public List<String> getCollectionTypes() {
        return Collections.unmodifiableList(collectionTypes);
    }
    
    public void setCollectionTypes(List<String> collectionTypes) {
        this.collectionTypes = collectionTypes != null ? new ArrayList<>(collectionTypes) : new ArrayList<>();
    }
    
    /**
     * Adds a collection type to the class info.
     * @param collectionType the type contained in the collection (e.g., "Cliente" from List<Cliente>)
     */
    public void addCollectionType(String collectionType) {
        if (collectionType != null) {
            this.collectionTypes.add(collectionType);
            this.hasCollections = true;
        }
    }
    
    /**
     * Checks if this class has a collection containing the specified type.
     * @param typeName the type to check for
     * @return true if the class has a collection of the specified type
     */
    public boolean hasCollectionOf(String typeName) {
        return collectionTypes.contains(typeName);
    }
    
    // Enhanced getters and setters
    
    public List<FieldInfo> getFields() {
        return Collections.unmodifiableList(fields);
    }
    
    public void setFields(List<FieldInfo> fields) {
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
        updateHasIdField();
    }
    
    public void addField(FieldInfo field) {
        if (field != null) {
            this.fields.add(field);
            if (field.isIdField()) {
                this.hasIdField = true;
            }
        }
    }
    
    public List<String> getInterfaces() {
        return Collections.unmodifiableList(interfaces);
    }
    
    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces != null ? new ArrayList<>(interfaces) : new ArrayList<>();
    }
    
    public void addInterface(String interfaceName) {
        if (interfaceName != null) {
            this.interfaces.add(interfaceName);
        }
    }
    
    public String getSuperClass() {
        return superClass;
    }
    
    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }
    
    public boolean hasIdField() {
        return hasIdField;
    }
    
    public void setHasIdField(boolean hasIdField) {
        this.hasIdField = hasIdField;
    }
    
    public List<String> getConstructorParams() {
        return Collections.unmodifiableList(constructorParams);
    }
    
    public void setConstructorParams(List<String> constructorParams) {
        this.constructorParams = constructorParams != null ? new ArrayList<>(constructorParams) : new ArrayList<>();
    }
    
    public void addConstructorParam(String param) {
        if (param != null) {
            this.constructorParams.add(param);
        }
    }
    
    /**
     * Checks if this class has a constructor that takes the specified type.
     */
    public boolean hasConstructorInjection(String typeName) {
        return constructorParams.contains(typeName);
    }
    
    /**
     * Gets the count of fields in this class.
     */
    public int getFieldCount() {
        return fields.isEmpty() ? fieldTypes.size() : fields.size();
    }
    
    private void updateHasIdField() {
        this.hasIdField = fields.stream().anyMatch(FieldInfo::isIdField);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassInfo classInfo = (ClassInfo) o;
        return hasCollections == classInfo.hasCollections &&
               Objects.equals(className, classInfo.className) &&
               Objects.equals(fieldTypes, classInfo.fieldTypes) &&
               Objects.equals(collectionTypes, classInfo.collectionTypes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(className, fieldTypes, hasCollections, collectionTypes);
    }
    
    @Override
    public String toString() {
        return "ClassInfo{" +
                "className='" + className + '\'' +
                ", fieldTypes=" + fieldTypes +
                ", hasCollections=" + hasCollections +
                ", collectionTypes=" + collectionTypes +
                '}';
    }
}
