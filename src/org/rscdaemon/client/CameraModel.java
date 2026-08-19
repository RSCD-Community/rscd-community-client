package org.rscdaemon.client;

/*
 * Despite the name this is not a camera: it is one visible face queued for
 * drawing. Each frame Camera projects every face of every model, and each
 * face that survives clipping gets one of these records. Camera then
 * quicksorts them by depth and runs an overlap-correction pass over the
 * result before painting back to front.
 */
public class CameraModel {
   // Projected bounding box: screen-space extent and view-space depth range,
   // filled in by Camera when the face is queued. The overlap pass only
   // compares faces whose boxes intersect.
   protected int minScreenX;
   protected int minScreenY;
   protected int maxScreenX;
   protected int maxScreenY;
   protected int minDepth;
   protected int maxDepth;
   // Which face this record is for.
   protected Model model;
   protected int faceIndex;
   // Average vertex depth; the key for the initial depth sort.
   protected int depth;
   // Face normal in view space, and the normal dotted with the first vertex
   // (the plane offset). The sign of visibility says which side of the face
   // the camera is on, which picks the front or back fill colour.
   protected int normalX;
   protected int normalY;
   protected int normalZ;
   protected int visibility;
   // Fill colour or texture id for the visible side; Camera skips faces
   // whose fill is the invisible marker 12345678 before one is queued.
   protected int faceFill;
   // Overlap-correction bookkeeping: ordered marks a face whose ordering has
   // been checked, index is its position when the pass started, and
   // lastSwapIndex remembers the face it was last reordered against so the
   // same pair cannot swap back and forth forever.
   protected boolean ordered = false;
   protected int index;
   protected int lastSwapIndex = -1;
}
