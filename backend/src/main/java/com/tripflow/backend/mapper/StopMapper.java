package com.tripflow.backend.mapper;

import org.springframework.stereotype.Component;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;

@Component
public class StopMapper {
	 public Stop toEntity(CreateStopRequest request, Place place, Integer stopOrder) {
	        Stop stop = new Stop();
	        stop.setPlace(place);
	        stop.setStopOrder(stopOrder);
	        stop.setNotes(request.notes());
	        // status intentionally NOT set from request — defaults to PLANNED on the entity
	        return stop;
	    }

	    public StopResponse toResponse(Stop stop) {
	        return new StopResponse(
	                stop.getId(),
	                stop.getPlace().getName(),
	                stop.getPlace().getLatitude(),
	                stop.getPlace().getLongitude(),
	                stop.getPlace().getAddress(),
	                stop.getStopOrder(),
	                stop.getStatus(),
	                stop.getNotes()
	        );
	    }

}
