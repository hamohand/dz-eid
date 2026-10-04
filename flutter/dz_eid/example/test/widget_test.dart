import 'package:dz_eid_example/main.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('accueil : NFC activé, bouton de lecture actif', (WidgetTester tester) async {
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('com.muhend.dzeid/dz_eid'),
      (call) async => call.method == 'getNfcStatus' ? 'AVAILABLE' : null,
    );

    await tester.pumpWidget(const DzEidDemoApp());
    await tester.pumpAndSettle();

    expect(find.text('NFC activé : prêt à lire.'), findsOneWidget);
    expect(find.text('Lire une carte'), findsOneWidget);
  });
}
