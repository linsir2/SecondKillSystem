import { BrowserRouter, Routes, Route } from 'react-router-dom'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<div>秒杀系统</div>} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
