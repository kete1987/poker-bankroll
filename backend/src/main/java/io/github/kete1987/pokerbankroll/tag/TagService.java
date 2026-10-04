package io.github.kete1987.pokerbankroll.tag;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaBuilder.In;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagService {

    /** By name ignoring case; the id only makes the order stable. */
    private static final Comparator<TagResponse> BY_NAME = Comparator
            .comparing(TagResponse::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(TagResponse::name)
            .thenComparingLong(TagResponse::id);

    private final TagRepository tags;
    private final EntityManager entityManager;

    TagService(TagRepository tags, EntityManager entityManager) {
        this.tags = tags;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> list() {
        return tags.findAllWithGames().stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.getGames()))
                .sorted(BY_NAME)
                .toList();
    }

    /**
     * The tags with these names, created when they do not exist. Names are stripped and matched
     * ignoring case, so an existing tag keeps how it is written; names repeated ignoring case are one
     * tag. Blank names are left out. Names are expected to be valid ({@link TagName}).
     */
    public List<Tag> resolve(@Nullable Collection<String> names) {
        if (names == null) {
            return List.of();
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                byKey.putIfAbsent(key(name), name.strip());
            }
        }
        if (byKey.isEmpty()) {
            return List.of();
        }
        List<Tag> found = findByNames(byKey.values());
        if (found.size() < byKey.size()) {
            // Some are new. Each is inserted unless it exists, also when another request has just
            // created it: then this one waits for it and finds it afterwards.
            byKey.values().forEach(tags::insertIfAbsent);
            found = findByNames(byKey.values());
        }
        return found;
    }

    /**
     * Renames a tag. When another tag already has the name (ignoring case), the tag is merged into
     * it: its games get the other tag and it is deleted. Returns the tag the games have now.
     */
    public TagResponse rename(long id, TagRequest request) {
        Tag tag = find(id);
        String name = request.name().strip();
        Optional<Tag> other = findByNames(List.of(name)).stream()
                .filter(one -> !one.getId().equals(tag.getId()))
                .findFirst();
        if (other.isPresent()) {
            Tag target = other.get();
            tags.copyGames(tag.getId(), target.getId());
            // Its rows in game_tag go with it.
            tags.delete(tag);
            tags.flush();
            return response(target);
        }
        tag.setName(name);
        return response(tags.saveAndFlush(tag));
    }

    /** Deletes a tag: its games lose it. */
    public void delete(long id) {
        tags.delete(find(id));
    }

    private TagResponse response(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tags.countGames(tag.getId()));
    }

    private Tag find(long id) {
        return tags.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** The tags whose name is one of these ignoring case, compared by the database. */
    private List<Tag> findByNames(Collection<String> names) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tag> query = cb.createQuery(Tag.class);
        Root<Tag> tag = query.from(Tag.class);
        In<String> in = cb.in(cb.lower(tag.get("name")));
        names.forEach(name -> in.value(cb.lower(cb.literal(name))));
        return entityManager.createQuery(query.select(tag).where(in)).getResultList();
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
