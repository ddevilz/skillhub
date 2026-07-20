import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');

  function update(field) {
    return (e) => setForm({ ...form, [field]: e.target.value });
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      await register(form);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message ?? 'Registration failed');
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <h1>Create account</h1>
      <label>Full name
        <input value={form.fullName} onChange={update('fullName')} required />
      </label>
      <label>Email
        <input type="email" value={form.email} onChange={update('email')} required />
      </label>
      <label>Password
        <input type="password" value={form.password} onChange={update('password')} minLength={8} required />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">Register</button>
      <p>Have an account? <Link to="/login">Log in</Link></p>
    </form>
  );
}
