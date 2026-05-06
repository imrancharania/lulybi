package com.lulybi.core.processor;

import com.google.auto.service.AutoService;
import com.lulybi.core.annotation.Column;
import com.lulybi.core.annotation.Partition;
import com.lulybi.core.annotation.Table;
import com.squareup.javapoet.*;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.lulybi.core.annotation.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class TableProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Table.class)) {
      if (element.getKind() == ElementKind.RECORD) {
        TypeElement recordElement = (TypeElement) element;
        try {
          generateMapper(recordElement);
          generateColumnConstants(recordElement);
        } catch (IOException e) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR, "Failed to generate Lulybi code: " + e.getMessage());
        }
      }
    }
    return true;
  }

  private void generateMapper(TypeElement record) throws IOException {
    String packageName = processingEnv.getElementUtils().getPackageOf(record).toString();
    String recordName = record.getSimpleName().toString();
    String mapperName = recordName + "Mapper";
    Table tableAnno = record.getAnnotation(Table.class);

    // 1. Prepare the map() method argument list (Constructor arguments)
    CodeBlock.Builder constructorArgs = CodeBlock.builder();
    var components = record.getRecordComponents();

    // 1.5 Prepare Column and Partition Definitions
    CodeBlock.Builder columnDefs = CodeBlock.builder();
    columnDefs.addStatement(
        "$T<$T, $T> defs = new $T<>()", Map.class, String.class, String.class, LinkedHashMap.class);

    CodeBlock.Builder partitionDefs = CodeBlock.builder();
    partitionDefs.addStatement(
        "$T<$T, $T> pDefs = new $T<>()",
        Map.class,
        String.class,
        String.class,
        LinkedHashMap.class);

    for (int i = 0; i < components.size(); i++) {
      var comp = components.get(i);
      Column colAnno = comp.getAnnotation(Column.class);
      Partition partAnno = comp.getAnnotation(Partition.class);

      String colName = (colAnno != null) ? colAnno.value() : comp.getSimpleName().toString();
      String type = comp.asType().toString();
      String sqlType = toSqlType(type);

      if (partAnno != null) {
        partitionDefs.addStatement("pDefs.put($S, $S)", colName, sqlType);
      } else {
        columnDefs.addStatement("defs.put($S, $S)", colName, sqlType);
      }

      // Use TypeConverter for type-safe extraction from the Athena Map
      switch (type) {
        case "int" ->
            constructorArgs.add(
                "$T.asInt(row.get($S), $S)",
                ClassName.get("com.lulybi.core.mapping", "TypeConverter"),
                colName,
                colName);
        case "long" ->
            constructorArgs.add(
                "$T.asLong(row.get($S), $S)",
                ClassName.get("com.lulybi.core.mapping", "TypeConverter"),
                colName,
                colName);
        case "java.time.Instant" ->
            constructorArgs.add(
                "$T.asInstant(row.get($S), $S)",
                ClassName.get("com.lulybi.core.mapping", "TypeConverter"),
                colName,
                colName);
        case "boolean" ->
            constructorArgs.add(
                "$T.asBoolean(row.get($S), $S)",
                ClassName.get("com.lulybi.core.mapping", "TypeConverter"),
                colName,
                colName);
        default -> constructorArgs.add("row.get($S)", colName); // Default to String
      }

      if (i < components.size() - 1) {
        constructorArgs.add(",\n"); // Multi-line for readability
      }
    }
    columnDefs.addStatement("return $T.unmodifiableMap(defs)", Collections.class);
    partitionDefs.addStatement("return $T.unmodifiableMap(pDefs)", Collections.class);

    // 2. Build the TypeSpec
    TypeSpec mapperClass =
        TypeSpec.classBuilder(mapperName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(
                ParameterizedTypeName.get(
                    ClassName.get("com.lulybi.core.mapping", "Mapper"), ClassName.get(record)))

            // The Static Block for Auto-Registration
            .addStaticBlock(
                CodeBlock.builder()
                    .addStatement(
                        "$T.register($T.class, new $L())",
                        ClassName.get("com.lulybi.core.registry", "MapperRegistry"),
                        ClassName.get(record),
                        mapperName)
                    .build())

            // getTableName() implementation
            .addMethod(
                MethodSpec.methodBuilder("getTableName")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addStatement("return $S", Objects.requireNonNull(tableAnno).tableName())
                    .build())

            // getDatabaseName() implementation
            .addMethod(
                MethodSpec.methodBuilder("getDatabaseName")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(String.class)
                    .addStatement("return $S", tableAnno.database())
                    .build())

            // getColumnDefinitions() implementation
            .addMethod(
                MethodSpec.methodBuilder("getColumnDefinitions")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(Map.class, String.class, String.class))
                    .addCode(columnDefs.build())
                    .build())

            // getPartitionDefinitions() implementation
            .addMethod(
                MethodSpec.methodBuilder("getPartitionDefinitions")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(Map.class, String.class, String.class))
                    .addCode(partitionDefs.build())
                    .build())

            // map(Map<String, String> row) implementation
            .addMethod(
                MethodSpec.methodBuilder("map")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get(record))
                    .addParameter(
                        ParameterizedTypeName.get(java.util.Map.class, String.class, String.class),
                        "row")
                    .addCode("return new $T(\n", ClassName.get(record))
                    .addCode(constructorArgs.indent().build())
                    .addCode("\n);\n")
                    .build())
            .build();

    // 3. Write to file
    JavaFile.builder(packageName, mapperClass)
        .skipJavaLangImports(true)
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private String toSqlType(String javaType) {
    return switch (javaType) {
      case "int", "java.lang.Integer" -> "INT";
      case "long", "java.lang.Long" -> "BIGINT";
      case "double", "java.lang.Double" -> "DOUBLE";
      case "boolean", "java.lang.Boolean" -> "BOOLEAN";
      case "java.time.Instant", "java.time.LocalDateTime", "java.time.OffsetDateTime" ->
          "TIMESTAMP";
      case "java.time.LocalDate" -> "DATE";
      default -> "STRING";
    };
  }

  private void generateColumnConstants(TypeElement record) throws IOException {
    String packageName = processingEnv.getElementUtils().getPackageOf(record).toString();
    String recordName = record.getSimpleName().toString();
    ClassName fieldClass = ClassName.get("com.lulybi.core.query", "Field");

    TypeSpec.Builder colClassBuilder =
        TypeSpec.classBuilder(recordName + "_").addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    for (RecordComponentElement comp : record.getRecordComponents()) {
      Column colAnno = comp.getAnnotation(Column.class);
      String colValue = (colAnno != null) ? colAnno.value() : comp.getSimpleName().toString();
      String constantName = camelToSnake(comp.getSimpleName().toString());
      // Fallback if Field class is missing in classpath during generation
      // But we assume it will be there in the core module
      TypeName fieldType = ParameterizedTypeName.get(fieldClass, TypeName.get(comp.asType()).box());

      colClassBuilder.addField(
          FieldSpec.builder(fieldType, constantName)
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
              .initializer(
                  "new $T<>($S, $T.class)", fieldClass, colValue, TypeName.get(comp.asType()).box())
              .build());
    }

    JavaFile.builder(packageName, colClassBuilder.build())
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private String camelToSnake(String str) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (Character.isUpperCase(c) && i > 0) {
        result.append('_');
      }
      result.append(Character.toUpperCase(c));
    }
    return result.toString();
  }
}
