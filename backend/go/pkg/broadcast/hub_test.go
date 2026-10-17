package broadcast

import "testing"

func TestBoundedCoalescingAndOwnership(t *testing.T) {
	h := New(2, 1)
	one, release, err := h.Watch("a", "one")
	if err != nil {
		t.Fatal(err)
	}
	defer release()
	if _, _, err = h.Watch("b", "one"); err == nil {
		t.Fatal("owner bound")
	}
	two, finish, err := h.Watch("b", "two")
	if err != nil {
		t.Fatal(err)
	}
	defer finish()
	if _, _, err = h.Watch("c", "three"); err == nil {
		t.Fatal("global bound")
	}
	for range 1000 {
		h.Publish("a")
	}
	if len(one) != 1 || len(two) != 0 {
		t.Fatal("hint coalescing/scope")
	}
	release()
	release()
	if _, open := <-one; open {
		_, open = <-one
		if open {
			t.Fatal("release did not close")
		}
	}
	if err = h.Close(); err != nil {
		t.Fatal(err)
	}
	if _, _, err = h.Watch("a", "one"); err == nil {
		t.Fatal("closed hub admitted subscription")
	}
}
