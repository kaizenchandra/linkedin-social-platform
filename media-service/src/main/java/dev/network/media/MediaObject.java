package dev.network.media;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "media_objects")
public class MediaObject {
  @Id public String id;
  public String ownerId,
      objectKey,
      state,
      contentType,
      resourceType,
      resourceId,
      operationId,
      operationMediaIds;
  public Long byteSize;
  public Integer width, height;
  public Instant createdAt, updatedAt;
  @Version public long version;
}
