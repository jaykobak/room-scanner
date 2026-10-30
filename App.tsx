import React, {useState} from 'react';
import {
  NativeModules,
  PermissionsAndroid,
  SafeAreaView,
  Text,
  TouchableOpacity,
  StyleSheet,
} from 'react-native';

const {ArCoreModule} = NativeModules;

export default function App() {
  const [status, setStatus] = useState('Ready to check depth support');

  const checkDepth = async () => {
    try {
      setStatus('Requesting camera permission...');

      const permission = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.CAMERA,
      );

      if (permission !== PermissionsAndroid.RESULTS.GRANTED) {
        setStatus('Camera permission denied');
        return;
      }

      setStatus('Checking Depth API support...');

      const supported = await ArCoreModule.checkDepthSupport();

      setStatus(
        supported
          ? 'Depth API: SUPPORTED'
          : 'Depth API: NOT SUPPORTED',
      );
    } catch (error) {
      setStatus(`Error: ${String(error)}`);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Room Scanner</Text>

      <Text style={styles.status}>{status}</Text>

      <TouchableOpacity style={styles.button} onPress={checkDepth}>
        <Text style={styles.buttonText}>Check Depth Support</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 26,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  status: {
    fontSize: 16,
    marginBottom: 24,
    textAlign: 'center',
  },
  button: {
    backgroundColor: '#2563eb',
    padding: 16,
    borderRadius: 10,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: 'bold',
  },
});