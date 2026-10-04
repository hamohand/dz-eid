import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'dz_eid_method_channel.dart';

abstract class DzEidPlatform extends PlatformInterface {
  /// Constructs a DzEidPlatform.
  DzEidPlatform() : super(token: _token);

  static final Object _token = Object();

  static DzEidPlatform _instance = MethodChannelDzEid();

  /// The default instance of [DzEidPlatform] to use.
  ///
  /// Defaults to [MethodChannelDzEid].
  static DzEidPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [DzEidPlatform] when
  /// they register themselves.
  static set instance(DzEidPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
