package br.com.urbana.connect.domain.servicecatalog.model;

import java.util.List;

public enum ServiceType {
    DECOR_INTERIORES,
    DECOR_PINTURA,
    DECOR_FACHADA,
    DECOR_REFORMA,
    /**
     * Kept only as an input/storage compatibility symbol. It is never part of
     * the canonical catalog and must not be presented as a service.
     */
    @Deprecated
    DECOR;

    public boolean isCanonical() {
        return this != DECOR;
    }

    public static List<ServiceType> canonicalValues() {
        return List.of(DECOR_INTERIORES, DECOR_PINTURA, DECOR_FACHADA, DECOR_REFORMA);
    }

    public static ServiceType canonicalize(ServiceType type) {
        if (type == null) {
            return null;
        }
        return type == DECOR ? DECOR_INTERIORES : type;
    }
}
