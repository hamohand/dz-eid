
import 'dz_eid_platform_interface.dart';

class DzEid {
  Future<String?> getPlatformVersion() {
    return DzEidPlatform.instance.getPlatformVersion();
  }
}
