package net.protsenko.spotfetchprice.mapper;

import java.util.List;

public interface Mappable<E, D> {

    D toDto(E entity);

    E toEntity(D dto);

    default List<D> toDto(List<E> entities) {
        return entities.stream()
                .map(this::toDto)
                .toList();
    }

}
