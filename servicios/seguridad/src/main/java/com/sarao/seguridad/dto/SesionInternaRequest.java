package com.sarao.seguridad.dto;

public record SesionInternaRequest(String token) {

	@Override
	public String toString() {
		return "SesionInternaRequest[]";
	}
}
