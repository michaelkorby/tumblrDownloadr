/**
 * Recognized EXIF Orientation values and their corresponding rotation angles that return the image to the correct orientation
 */
public enum Orientation {

    Normal("OUTPUT>Orientation: Horizontal (normal)\n", 0),
    Blank("", 0),
    Unknown("OUTPUT>Orientation: Unknown (0)\n",0),
	NinetyDegreeClockwise("OUTPUT>Orientation: Rotate 90 CW\n", -90),
	OneEightyDegrees("OUTPUT>Orientation: Rotate 180\n",180);

    private String orientation;

    private double rotationAngle;

    Orientation(final String orientation, final int rotationAngle) {
        this.orientation = orientation;
        this.rotationAngle = rotationAngle;
    }
    public double getRotationAngle() {
        return rotationAngle;
    }

    public static Orientation getOrientation(final String orientation) {
        for (final Orientation orientationOption : values()) {
            if (orientationOption.orientation.equals(orientation)) {
                return orientationOption;
            }
        }
        return null;
    }

}