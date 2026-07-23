import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';

function PostRow({ p, onSelect }) {
  return (
    <Card>
      <CardContent className="space-y-1 py-4">
        <button
          type="button"
          onClick={() => onSelect(p.id)}
          className="font-medium underline-offset-4 hover:underline text-left"
        >
          {p.title}
        </button>
        <p className="text-sm text-muted-foreground">
          by {p.authorName} · {p.createdDate}
        </p>
        <div className="flex gap-2">
          <Badge variant="secondary">{p.upvoteCount} upvotes</Badge>
          <Badge variant="secondary">{p.commentCount} comments</Badge>
        </div>
      </CardContent>
    </Card>
  );
}

function PostDetail({ postId, onBack, onDeleted }) {
  const { user } = useAuth();
  const [post, setPost] = useState(null);
  const [comments, setComments] = useState([]);
  const [upvotedPostIds, setUpvotedPostIds] = useState(new Set());
  const [commentText, setCommentText] = useState('');
  const [error, setError] = useState('');

  function loadComments() {
    api.get(`/forum/posts/${postId}/comments`).then((res) => setComments(res.data)).catch(() => {});
  }

  useEffect(() => {
    api.get(`/forum/posts/${postId}`).then((res) => setPost(res.data)).catch(() => {});
    loadComments();
  }, [postId]);

  async function upvote() {
    setError('');
    try {
      const res = await api.post(`/forum/posts/${postId}/upvote`);
      setPost(res.data);
      setUpvotedPostIds((prev) => new Set(prev).add(postId));
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not upvote');
    }
  }

  async function deletePost() {
    setError('');
    try {
      await api.delete(`/forum/posts/${postId}`);
      onDeleted();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete post');
    }
  }

  async function deleteComment(commentId) {
    setError('');
    try {
      await api.delete(`/forum/comments/${commentId}`);
      loadComments();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete comment');
    }
  }

  async function addComment(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post(`/forum/posts/${postId}/comments`, { commentText });
      setCommentText('');
      loadComments();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not add comment');
    }
  }

  if (!post) return null;

  return (
    <div className="space-y-4">
      <Button variant="ghost" size="sm" onClick={onBack}>Back to Forum</Button>
      <Card>
        <CardContent className="space-y-2 py-4">
          <h2 className="text-xl font-semibold">{post.title}</h2>
          <p className="text-sm text-muted-foreground">by {post.authorName} · {post.createdDate}</p>
          <p>{post.content}</p>
          <div className="flex gap-2 pt-2">
            <Button size="sm" disabled={upvotedPostIds.has(postId)} onClick={upvote}>
              {upvotedPostIds.has(postId) ? 'Upvoted' : `Upvote (${post.upvoteCount})`}
            </Button>
            {user && post.userId === user.id && (
              <Button size="sm" variant="outline" onClick={deletePost}>Delete</Button>
            )}
          </div>
        </CardContent>
      </Card>

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

      <div className="space-y-3">
        <h3 className="font-semibold">Comments</h3>
        {comments.map((c) => (
          <Card key={c.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm">{c.commentText}</p>
                <p className="text-xs text-muted-foreground">{c.authorName} · {c.createdDate}</p>
              </div>
              {user && c.userId === user.id && (
                <Button size="sm" variant="ghost" onClick={() => deleteComment(c.id)}>Delete</Button>
              )}
            </CardContent>
          </Card>
        ))}
        {comments.length === 0 && <p className="text-sm text-muted-foreground">No comments yet.</p>}

        <form onSubmit={addComment} className="space-y-2">
          <textarea
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            rows={2}
            className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
            placeholder="Add a comment..."
          />
          <Button type="submit" size="sm" disabled={!commentText.trim()}>Comment</Button>
        </form>
      </div>
    </div>
  );
}

export default function Forum() {
  const [categories, setCategories] = useState([]);
  const [postsByCategory, setPostsByCategory] = useState({});
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [selectedPostId, setSelectedPostId] = useState(null);

  useEffect(() => {
    api.get('/forum/categories').then((res) => {
      setCategories(res.data);
      if (res.data.length > 0) setActiveCategoryId(String(res.data[0].id));
    }).catch(() => {});
  }, []);

  function loadCategoryPosts(categoryId) {
    api.get(`/forum/categories/${categoryId}/posts`).then((res) => {
      setPostsByCategory((prev) => ({ ...prev, [categoryId]: res.data }));
    }).catch(() => {});
  }

  useEffect(() => {
    if (activeCategoryId && !(activeCategoryId in postsByCategory)) {
      loadCategoryPosts(activeCategoryId);
    }
  }, [activeCategoryId]);

  async function searchPosts(keyword) {
    try {
      const res = await api.get('/forum/posts/search', { params: { keyword } });
      setSearchResults(res.data);
      setSearchKeyword(keyword);
    } catch {
      setSearchResults([]);
      setSearchKeyword(keyword);
    }
  }

  async function runSearch(e) {
    e.preventDefault();
    const keyword = searchInput.trim();
    if (!keyword) return;
    await searchPosts(keyword);
  }

  function clearSearch() {
    setSearchKeyword('');
    setSearchInput('');
    setSearchResults([]);
  }

  const [newOpen, setNewOpen] = useState(false);
  const [newForm, setNewForm] = useState({ categoryId: '', title: '', content: '' });
  const [newError, setNewError] = useState('');

  async function createPost(e) {
    e.preventDefault();
    setNewError('');
    try {
      await api.post(`/forum/categories/${newForm.categoryId}/posts`, {
        title: newForm.title,
        content: newForm.content,
      });
      setNewOpen(false);
      setNewForm({ categoryId: '', title: '', content: '' });
      if (newForm.categoryId === activeCategoryId) loadCategoryPosts(activeCategoryId);
    } catch (err) {
      setNewError(err.response?.data?.message ?? 'Could not create post');
    }
  }

  if (selectedPostId) {
    return (
      <PostDetail
        postId={selectedPostId}
        onBack={() => setSelectedPostId(null)}
        onDeleted={() => {
          setSelectedPostId(null);
          if (searchKeyword) {
            searchPosts(searchKeyword);
          } else if (activeCategoryId) {
            loadCategoryPosts(activeCategoryId);
          }
        }}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Forum</h1>
        <Dialog open={newOpen} onOpenChange={setNewOpen}>
          <DialogTrigger asChild>
            <Button>New Post</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={createPost} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Create a post</DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="post-category">Category</Label>
                <Select value={newForm.categoryId} onValueChange={(v) => setNewForm({ ...newForm, categoryId: v })}>
                  <SelectTrigger id="post-category"><SelectValue placeholder="Choose a category" /></SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>{c.categoryName}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="post-title">Title</Label>
                <Input
                  id="post-title"
                  value={newForm.title}
                  onChange={(e) => setNewForm({ ...newForm, title: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="post-content">Content</Label>
                <textarea
                  id="post-content"
                  value={newForm.content}
                  onChange={(e) => setNewForm({ ...newForm, content: e.target.value })}
                  rows={4}
                  className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                />
              </div>
              {newError && <p role="alert" className="text-sm text-destructive">{newError}</p>}
              <DialogFooter>
                <Button
                  type="submit"
                  disabled={!newForm.categoryId || !newForm.title || !newForm.content}
                >
                  Post
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <form onSubmit={runSearch} className="flex gap-2">
        <Input
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search posts..."
        />
        <Button type="submit" variant="outline">Search</Button>
      </form>

      {searchKeyword ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">Search results for "{searchKeyword}"</p>
            <Button size="sm" variant="ghost" onClick={clearSearch}>Clear search</Button>
          </div>
          {searchResults.map((p) => <PostRow key={p.id} p={p} onSelect={setSelectedPostId} />)}
          {searchResults.length === 0 && <p className="text-sm text-muted-foreground">No posts found.</p>}
        </div>
      ) : (
        <Tabs value={activeCategoryId ?? undefined} onValueChange={setActiveCategoryId}>
          <TabsList>
            {categories.map((c) => (
              <TabsTrigger key={c.id} value={String(c.id)}>{c.categoryName}</TabsTrigger>
            ))}
          </TabsList>
          {categories.map((c) => (
            <TabsContent key={c.id} value={String(c.id)} className="space-y-3">
              {(postsByCategory[c.id] ?? []).map((p) => <PostRow key={p.id} p={p} onSelect={setSelectedPostId} />)}
              {(postsByCategory[c.id] ?? []).length === 0 && (
                <p className="text-sm text-muted-foreground">No posts yet in this category.</p>
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  );
}
