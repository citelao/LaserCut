import java.awt.geom.Rectangle2D;

public class MicroLaser extends MiniLaser{

  MicroLaser (LaserCut laserCut) {
    super(laserCut);
  }

  // Implement for GRBLBase to define Preferences prefix, such as "mini.laser."
  String getPrefix () {
    return "micro.laser.";
  }

  // Implement for LaserCut.OutputDevice
  public String getName () {
    return "Micro Laser";
  }

  // Implement for LaserCut.OutputDevice
  @Override
  public Rectangle2D.Double getWorkspaceSize () {
    return new Rectangle2D.Double(0, 0, getDouble("workwidth", 1.5), getDouble("workheight", 1.5));
  }

  // Implement for LaserCut.OutputDevice
  @Override
  public double getZoomFactor () {
    return getDouble("workzoom", 4.0);
  }
}
