// Test d'intégration : s'exécute sur un téléphone réel (`flutter test integration_test`).

import 'package:dz_eid/dz_eid.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('le canal natif répond (statut NFC)', (WidgetTester tester) async {
    final status = await DzEid.getNfcStatus();
    expect(NfcStatus.values, contains(status));
  });
}
