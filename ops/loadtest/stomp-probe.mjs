// Node 22+: exercise both STOMP channel executors before the load test's thread dump.
// Uses only the disposable local seed database. Credentials arrive via environment, never output.
const socket = new WebSocket(`${process.env.API_URL.replace('http:', 'ws:')}/ws/websocket`)
const timeout = setTimeout(() => {
  console.error('STOMP executor probe timed out')
  process.exit(1)
}, 10000)
socket.addEventListener('open', () => {
  socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${process.env.TOKEN}\nheart-beat:0,0\n\n\0`)
})
socket.addEventListener('error', () => {
  console.error('STOMP executor probe failed')
  process.exit(1)
})
socket.addEventListener('message', ({ data }) => {
  if (data.startsWith('CONNECTED')) {
    socket.send(`SUBSCRIBE\nid:probe\ndestination:/topic/pelada/${process.env.PELADA_ID}\n\n\0`)
    // The simple broker does not support receipts. Give the subscription time to reach its channel handler.
    setTimeout(() => {
      const body = JSON.stringify({ content: 'Sonda local de executores STOMP' })
      socket.send(`SEND\ndestination:/app/pelada/${process.env.PELADA_ID}/send\ncontent-type:application/json\n\n${body}\0`)
    }, 250)
  } else if (data.startsWith('MESSAGE')) {
    clearTimeout(timeout)
    socket.close()
    console.log('STOMP CONNECT / SUBSCRIBE / SEND / MESSAGE succeeded')
  } else if (data.startsWith('ERROR')) {
    console.error('STOMP executor probe rejected')
    process.exit(1)
  }
})
