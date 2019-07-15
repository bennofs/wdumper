package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.Lists;
import kong.unirest.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Zenodo {
    private final URI baseUri;

    private final UnirestInstance unirest;

    public Zenodo(String baseUri, String accessToken) {
        this.baseUri = URI.create(baseUri);
        this.unirest = Unirest.spawnInstance();

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setInjectableValues(new InjectableValues.Std().addValue("unirest", this.unirest));

        this.unirest.config()
                .setDefaultHeader("Authorization", "Bearer " + accessToken)
                .proxy("localhost", 8080)
                .verifySsl(false)
                .setObjectMapper(new JacksonObjectMapper(mapper));
    }

    private String endpoint(String suburi) {
        return this.baseUri.resolve(suburi).toString();
    }

    static <T> void handleError(HttpResponse<T> response) throws ZenodoException {
        String message = "http request failed: status code " + response.getStatus() + " " + response.getStatusText();

        message = message + ": " + response.getParsingError()
                .map(exception -> "response body failed to parse: " + exception)
                .orElse("body: " + Optional.ofNullable(response.getBody()).map(Object::toString).orElse("<null>"));

        throw new ZenodoException(message);
    }

    public Deposit createDeposit(String title, String description) throws ZenodoException {
        final HttpResponse<Deposit> response = this.unirest.post(endpoint("deposit/depositions"))
                .header("Content-Type", "application/json")
                .body(Map.of())
                .asObject(Deposit.class);

        if (!response.isSuccess()) handleError(response);

        final Deposit d = response.getBody();

        d.putMetadata(title, description, Lists.newArrayList(new Deposit.Creator("Fünfstück, Benno", null)));
        return response.getBody();
    }

    public Deposit getDeposit(int id) throws ZenodoException {
        final HttpResponse<Deposit> response = this.unirest.get(endpoint("deposit/depositions/" + id))
                .header("Content-Type", "application/json")
                .asObject(Deposit.class);
        if (!response.isSuccess()) handleError(response);

        return response.getBody();
    }

    public List<Deposit> getRecentDepositions() throws ZenodoException {
        final HttpResponse<Deposit[]> response = this.unirest.get(endpoint("deposit/depositions"))
                .header("Content-Type", "application/json")
                .asObject(Deposit[].class);
        if(!response.isSuccess()) handleError(response);

        return Lists.newArrayList(response.getBody());
    }
}
