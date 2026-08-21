package com.tripflow.backend.client.mapbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Mapbox Static Images API bean wiring. Unlike {@code OrsClientConfig}/{@code GeminiClientConfig},
 * no default {@code Authorization} header is set here: the Static Images API authenticates via an
 * {@code access_token} query parameter on every request (query auth, not header auth) — a
 * header-based default would silently 401 every call.
 */
@Configuration
@EnableConfigurationProperties(MapboxProperties.class)
public class MapboxClientConfig {

	@Bean
	public RestClient mapboxRestClient(MapboxProperties props, RestClient.Builder builder) {
		HttpClientSettings settings = HttpClientSettings.defaults()
				.withConnectTimeout(props.connectTimeout())
				.withReadTimeout(props.readTimeout());

		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

		return builder
				.baseUrl(props.baseUrl())
				.requestFactory(requestFactory)
				.build();
	}
}
