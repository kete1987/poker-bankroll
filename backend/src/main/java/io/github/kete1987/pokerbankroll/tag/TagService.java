package io.github.kete1987.pokerbankroll.tag;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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
                .map(tag -> new TagResponse(tag.getId(), tag.getName(), tag.getGames(),
                        tag.getColor() == null ? null : TagColor.valueOf(tag.getColor())))
                .sorted(BY_NAME)
                .toList();
    }

    /** One tag and its current game count. */
    @Transactional(readOnly = true)
    public TagResponse get(long id) {
        return response(find(id));
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
        // Sorted, so requests creating the same new tags insert them in the same order and wait for
        // each other instead of deadlocking on the unique index.
        Map<String, String> byKey = new TreeMap<>();
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

    /** Canonical spelling for a preview without creating missing tags. */
    public List<String> previewNames(Collection<String> names) {
        Map<String, String> result = new TreeMap<>();
        for (String name : names) result.putIfAbsent(key(name), name.strip());
        if (!result.isEmpty()) {
            for (Tag tag : findByNames(result.values())) result.put(key(tag.getName()), tag.getName());
        }
        return List.copyOf(result.values());
    }

    /** The existing merge target, using the same database comparison as rename. */
    public @Nullable TagResponse previewTarget(long sourceId, String name) {
        return findByNames(List.of(name.strip())).stream().filter(tag -> tag.getId() != sourceId)
                .findFirst().map(this::response).orElse(null);
    }

    /**
     * Renames a tag. When another tag already has the name (ignoring case), the tag is merged into
     * it: its games get the other tag and it is deleted. Returns the tag the games have now.
     */
    public TagResponse rename(long id, TagRequest request) {
        Tag tag = findForUpdate(id);
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
        tags.delete(findForUpdate(id));
    }

    /** Changes no name or associations, including when another edit happens concurrently. */
    public TagResponse setColor(long id, TagColorRequest request) {
        Tag tag = findForUpdate(id);
        tag.setColor(request.color());
        return response(tags.saveAndFlush(tag));
    }

    private Tag findForUpdate(long id) {
        // Reserve the table lock needed by UPDATE/DELETE before holding a row lock. Otherwise
        // MCP maintenance can take SHARE ROW EXCLUSIVE between SELECT FOR UPDATE and the write,
        // then wait for our row while we wait to upgrade our table lock. ROW EXCLUSIVE permits
        // ordinary tag writes to run concurrently and also works inside MCP's stronger lock.
        entityManager.createNativeQuery("lock table tag in row exclusive mode").executeUpdate();
        return tags.findForUpdateById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private TagResponse response(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tags.countGames(tag.getId()), tag.getColor());
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
