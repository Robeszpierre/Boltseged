package hu.boltseged.label;
import java.io.*; import java.util.UUID;
/** Storage boundary deliberately uses opaque keys, so domain code never depends on filesystem paths. */
public interface LabelStorage { String put(UUID shipmentId,String fileName,String contentType,InputStream content) throws IOException; InputStream get(String storageKey) throws IOException; }
