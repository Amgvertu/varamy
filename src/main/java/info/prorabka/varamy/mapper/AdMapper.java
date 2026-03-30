package info.prorabka.varamy.mapper;

import info.prorabka.varamy.dto.request.AdRequest;
import info.prorabka.varamy.dto.response.AdResponse;
import info.prorabka.varamy.entity.Ad;
import info.prorabka.varamy.entity.City;
import org.mapstruct.*;

import java.util.Arrays;
import java.util.List;

@Mapper(componentModel = "spring", uses = {UserMapper.class, CityMapper.class, ResponseMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdMapper {

    @Mapping(source = "author", target = "author")
    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "city", target = "city")
    @Mapping(source = "levels", target = "level")
    @Mapping(source = "rinkIds", target = "rinkIds", qualifiedByName = "arrayToListLong")
    @Mapping(source = "responses", target = "responses")
    @Mapping(source = "subType", target = "subType")
    @Mapping(source = "endTime", target = "endTime")
    @Mapping(source = "showTeam", target = "showTeam")
    @Mapping(source = "goaliesCount", target = "goaliesCount")
    @Mapping(source = "defendersCount", target = "defendersCount")
    @Mapping(source = "forwardsCount", target = "forwardsCount")
    @Mapping(source = "acceptedGoaliesCount", target = "acceptedGoaliesCount")
    @Mapping(source = "acceptedDefendersCount", target = "acceptedDefendersCount")
    @Mapping(source = "acceptedForwardsCount", target = "acceptedForwardsCount")
    AdResponse toResponse(Ad ad);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    @Mapping(target = "levels", source = "level")
    @Mapping(target = "rinkIds", source = "rinkIds", qualifiedByName = "listToArrayLong")
    @Mapping(target = "city", source = "cityId", qualifiedByName = "cityIdToCity")
    @Mapping(target = "subType", source = "subType")
    @Mapping(target = "endTime", source = "endTime")
    @Mapping(target = "showTeam", source = "showTeam")
    @Mapping(target = "goaliesCount", source = "goaliesCount")
    @Mapping(target = "defendersCount", source = "defendersCount")
    @Mapping(target = "forwardsCount", source = "forwardsCount")
    @Mapping(target = "acceptedGoaliesCount", ignore = true)
    @Mapping(target = "acceptedDefendersCount", ignore = true)
    @Mapping(target = "acceptedForwardsCount", ignore = true)
    Ad toEntity(AdRequest request, @Context City city);

    // Аналогично для updateAd
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    @Mapping(target = "levels", source = "level")
    @Mapping(target = "rinkIds", source = "rinkIds", qualifiedByName = "listToArrayLong")
    @Mapping(target = "city", source = "cityId", qualifiedByName = "cityIdToCity")
    @Mapping(target = "subType", source = "subType")
    @Mapping(target = "endTime", source = "endTime")
    @Mapping(target = "showTeam", source = "showTeam")
    @Mapping(target = "goaliesCount", source = "goaliesCount")
    @Mapping(target = "defendersCount", source = "defendersCount")
    @Mapping(target = "forwardsCount", source = "forwardsCount")
    @Mapping(target = "acceptedGoaliesCount", ignore = true)
    @Mapping(target = "acceptedDefendersCount", ignore = true)
    @Mapping(target = "acceptedForwardsCount", ignore = true)
    void updateAd(@MappingTarget Ad ad, AdRequest request, @Context City city);

    @Named("arrayToListLong")
    default List<Long> arrayToListLong(Long[] array) {
        return array != null ? Arrays.asList(array) : null;
    }

    @Named("listToArrayLong")
    default Long[] listToArrayLong(List<Long> list) {
        return list != null ? list.toArray(new Long[0]) : null;
    }

    @Named("cityIdToCity")
    default City cityIdToCity(Long cityId, @Context City city) {
        return city;
    }
}