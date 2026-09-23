package betamoon.debug;

/** Writes one catalog category through a managed debug-export session. */
interface DebugExporter {
    void export(DebugExportSession session) throws Exception;
}
