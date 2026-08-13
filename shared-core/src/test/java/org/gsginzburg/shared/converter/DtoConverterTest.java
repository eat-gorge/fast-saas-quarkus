package org.gsginzburg.shared.converter;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DtoConverterTest {

    public record SampleDto(String id, String name, int quantity, boolean active,
                            long total, double rate, Integer boxed) {}

    public static class SampleEntity {
        public UUID id;
        public String name;
        public int quantity;
        public boolean active;
        public long total;
        public double rate;
        public Integer boxed;

        public UUID getId()          { return id; }
        public void setId(UUID v)    { id = v; }
        public String getName()      { return name; }
        public void setName(String v){ name = v; }
        public int getQuantity()     { return quantity; }
        public void setQuantity(int v) { quantity = v; }
        public boolean isActive()    { return active; }
        public void setActive(boolean v) { active = v; }
        public long getTotal()       { return total; }
        public void setTotal(long v) { total = v; }
        public double getRate()      { return rate; }
        public void setRate(double v){ rate = v; }
        public Integer getBoxed()    { return boxed; }
        public void setBoxed(Integer v) { boxed = v; }
    }

    static class SampleConverter extends DtoConverter<SampleDto, SampleEntity> {
        SampleConverter() { super(SampleDto.class, SampleEntity.class); }
    }

    private final SampleConverter converter = new SampleConverter();

    @Test
    void toDomainCopiesPrimitives() {
        UUID id = UUID.randomUUID();

        SampleEntity entity = converter.toDomain(
                new SampleDto(id.toString(), "Cart", 30, true, 900L, 2.5d, 7));

        assertThat(entity.id).isEqualTo(id);
        assertThat(entity.name).isEqualTo("Cart");
        assertThat(entity.quantity).isEqualTo(30);
        assertThat(entity.active).isTrue();
        assertThat(entity.total).isEqualTo(900L);
        assertThat(entity.rate).isEqualTo(2.5d);
        assertThat(entity.boxed).isEqualTo(7);
    }

    @Test
    void toDtoCopiesPrimitives() {
        SampleEntity entity = new SampleEntity();
        entity.id = UUID.randomUUID();
        entity.name = "Cart";
        entity.quantity = 30;
        entity.active = true;
        entity.total = 900L;
        entity.rate = 2.5d;
        entity.boxed = 7;

        SampleDto dto = converter.toDto(entity);

        assertThat(dto.id()).isEqualTo(entity.id.toString());
        assertThat(dto.name()).isEqualTo("Cart");
        assertThat(dto.quantity()).isEqualTo(30);
        assertThat(dto.active()).isTrue();
        assertThat(dto.total()).isEqualTo(900L);
        assertThat(dto.rate()).isEqualTo(2.5d);
        assertThat(dto.boxed()).isEqualTo(7);
    }

    /** A null source value for a primitive component must not fail the whole conversion. */
    @Test
    void toDtoNullBoxedValueBecomesTheDefault() {
        SampleEntity entity = new SampleEntity();
        entity.name = "Cart";

        SampleDto dto = converter.toDto(entity);

        assertThat(dto.id()).isNull();
        assertThat(dto.quantity()).isZero();
        assertThat(dto.active()).isFalse();
        assertThat(dto.boxed()).isNull();
    }
}
