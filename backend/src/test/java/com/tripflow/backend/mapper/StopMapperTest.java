package com.tripflow.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;

public class StopMapperTest {

	private final StopMapper stopMapper = new StopMapper();

	@Test
	void toEntity_mapsPlaceStopOrderAndNotes() {
		Place place = new Place();
		place.setName("CN Tower");
		place.setLatitude(43.6426);
		place.setLongitude(-79.3871);

		CreateStopRequest request = new CreateStopRequest(
				"CN Tower", 43.6426, -79.3871, "301 Front St W", null, "Bring camera");

		Stop stop = stopMapper.toEntity(request, place, 2);

		assertThat(stop.getPlace()).isEqualTo(place);
		assertThat(stop.getStopOrder()).isEqualTo(2);
		assertThat(stop.getNotes()).isEqualTo("Bring camera");
	}

	@Test
	void toEntity_statusDefaultsToPlanned() {
		// CreateStopRequest has no status field at all — status is entirely
		// entity-owned (Stop's field initializer). This proves the mapper
		// neither reads nor overrides it; PLANNED comes from the entity itself.
		Place place = new Place();
		CreateStopRequest request = new CreateStopRequest("Place", 0.0, 0.0, null, null, null);

		Stop stop = stopMapper.toEntity(request, place, 1);

		assertThat(stop.getStatus()).isEqualTo(StopStatus.PLANNED);
	}

	@Test
	void toResponse_mapsAllPlaceAndStopFields() {
		Place place = new Place();
		place.setName("Niagara Falls");
		place.setLatitude(43.0962);
		place.setLongitude(-79.0377);
		place.setAddress("6650 Niagara Pkwy");

		Stop stop = new Stop();
		stop.setId(42L);
		stop.setPlace(place);
		stop.setStopOrder(1);
		stop.setStatus(StopStatus.VISITED);
		stop.setNotes("Loud but worth it");

		StopResponse response = stopMapper.toResponse(stop);

		assertThat(response.id()).isEqualTo(42L);
		assertThat(response.name()).isEqualTo("Niagara Falls");
		assertThat(response.latitude()).isEqualTo(43.0962);
		assertThat(response.longitude()).isEqualTo(-79.0377);
		assertThat(response.address()).isEqualTo("6650 Niagara Pkwy");
		assertThat(response.stopOrder()).isEqualTo(1);
		assertThat(response.status()).isEqualTo(StopStatus.VISITED);
		assertThat(response.notes()).isEqualTo("Loud but worth it");
	}

	@Test
	void toResponse_handlesNullNotes() {
		Place place = new Place();
		place.setName("Rest Stop");
		place.setLatitude(44.0);
		place.setLongitude(-80.0);

		Stop stop = new Stop();
		stop.setPlace(place);
		stop.setStopOrder(5);
		stop.setNotes(null);

		StopResponse response = stopMapper.toResponse(stop);

		assertThat(response.notes()).isNull();
	}
}
