package dinner.dev.pondus.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class QuaternionUtil {
    public static Quaternionf getViewRotation(float pitch, float yaw) {
        Quaternionf r1 = new Quaternionf().fromAxisAngleDeg(new Vector3f(1, 0, 0), pitch);
        Quaternionf r2 = new Quaternionf().fromAxisAngleDeg(new Vector3f(0, 1, 0), yaw + 180);
        r1.mul(r2);
        return r1;
    }
    
    public static Quaternionf getRotationBetween(Vec3 from, Vec3 to) {
        double fromLenSq = from.lengthSqr();
        double toLenSq = to.lengthSqr();
        if (fromLenSq < 1.0E-12 || toLenSq < 1.0E-12) {
            return new Quaternionf();
        }

        from = from.scale(1.0 / Math.sqrt(fromLenSq));
        to = to.scale(1.0 / Math.sqrt(toLenSq));
        double cos = Mth.clamp(from.dot(to), -1.0, 1.0);
        if (cos > 0.999999) {
            return new Quaternionf();
        }
        if (cos < -0.999999) {
            Vec3 perpendicular = Math.abs(from.x) < 0.9
                ? from.cross(new Vec3(1, 0, 0)).normalize()
                : from.cross(new Vec3(0, 1, 0)).normalize();
            return new Quaternionf().fromAxisAngleRad(
                new Vector3f((float) perpendicular.x, (float) perpendicular.y, (float) perpendicular.z),
                (float) Math.PI
            );
        }

        Vec3 axis = from.cross(to);
        double axisLenSq = axis.lengthSqr();
        if (axisLenSq < 1.0E-12) {
            return new Quaternionf();
        }
        axis = axis.scale(1.0 / Math.sqrt(axisLenSq));
        double angle = Math.acos(cos);
        return new Quaternionf().fromAxisAngleRad(
            new Vector3f((float) axis.x, (float) axis.y, (float) axis.z),
            (float) angle
        );
    }
    
    // using mutable objects could easily cause bugs if forget to copy
    public static Vec3 rotate(Vec3 vec, Quaternionf quaternionf) {
        Vector3f vector3f = vec.toVector3f();
        vector3f.rotate(quaternionf);
        return new Vec3(vector3f);
    }
}
