package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.spec.EntityFilter;

import java.io.IOException;

public class GenerateSchema {
    public static void main(String[] args) {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(mapper);

        try {
            mapper.writeValue(System.out, schemaGen.generateJsonSchema(DumpSpec.class));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }
}
