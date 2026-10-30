import React, {useEffect, useState} from 'react';
import {NativeModules, SafeAreaView, Text, StyleSheet} from 'react-native';

const {ArCoreModule} = NativeModules;

export default function App() {
  const [status, setStatus] = useState('Checking ARCore...');

  useEffect(() => {
    ArCoreModule.checkAvailability()
      .then((result: string) => setStatus(`ARCore status: ${result}`))
      .catch((error: unknown) => {
        setStatus(`ARCore check failed: ${String(error)}`);
      });
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Room Scanner</Text>
      <Text>{status}</Text>
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
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 16,
  },
});