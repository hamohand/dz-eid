import 'package:flutter_test/flutter_test.dart';
import 'package:dz_eid/dz_eid.dart';
import 'package:dz_eid/dz_eid_platform_interface.dart';
import 'package:dz_eid/dz_eid_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockDzEidPlatform
    with MockPlatformInterfaceMixin
    implements DzEidPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final DzEidPlatform initialPlatform = DzEidPlatform.instance;

  test('$MethodChannelDzEid is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelDzEid>());
  });

  test('getPlatformVersion', () async {
    DzEid dzEidPlugin = DzEid();
    MockDzEidPlatform fakePlatform = MockDzEidPlatform();
    DzEidPlatform.instance = fakePlatform;

    expect(await dzEidPlugin.getPlatformVersion(), '42');
  });
}
