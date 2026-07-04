package com.tripflow.backend.mapper;

import com.tripflow.backend.beans.Place;
import com.tripflow.backend.beans.Stop;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StopMapper {
	public static Stop toEntity(CreateStopRequest request, Place place, Integer stopOrder) {
        Stop stop = new Stop();
        stop.setPlace(place);
        stop.setStopOrder(stopOrder);
        stop.setNotes(request.getNotes());
        // status intentionally NOT set from request — defaults to PLANNED on the entity
        return stop;
    }

    public static StopResponse toResponse(Stop stop) {
        StopResponse response = new StopResponse();
        response.setId(stop.getId());
        response.setName(stop.getPlace().getName());
        response.setLatitude(stop.getPlace().getLatitude());
        response.setLongitude(stop.getPlace().getLongitude());
        response.setAddress(stop.getPlace().getAddress());
        response.setStopOrder(stop.getStopOrder());
        response.setStatus(stop.getStatus());
        response.setNotes(stop.getNotes());
        return response;
    }

}
